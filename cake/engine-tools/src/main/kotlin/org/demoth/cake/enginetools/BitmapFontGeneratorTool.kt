package org.demoth.cake.enginetools

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.PixmapPacker
import com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.tools.bmfont.BitmapFontWriter
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File

/**
 * Generates a libGDX bitmap font (`.fnt` + `.png`) from a TTF file.
 *
 * The character set defaults to [FreeTypeFontGenerator.DEFAULT_CHARS], matching the default libGDX skin font set.
 */
object BitmapFontGeneratorTool {
    @JvmStatic
    fun main(args: Array<String>) {
        GdxNativesLoader.load()

        val options = parseArgs(args) ?: run {
            printUsage()
            return
        }

        val ttfFile = File(options.ttfPath)
        if (!ttfFile.exists() || !ttfFile.isFile || !ttfFile.canRead()) {
            System.err.println("Input font is not readable: ${ttfFile.absolutePath}")
            kotlin.system.exitProcess(1)
        }

        val outputDir = File(options.outputDirectory)
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            System.err.println("Failed to create output directory: ${outputDir.absolutePath}")
            kotlin.system.exitProcess(1)
        }

        generate(ttfFile = ttfFile, outputDir = outputDir, options = options)
    }

    private fun generate(ttfFile: File, outputDir: File, options: Options) {
        val generator = FreeTypeFontGenerator(FileHandle(ttfFile))
        val pixmapPacker = PixmapPacker(
            options.pageWidth,
            options.pageHeight,
            Pixmap.Format.RGBA8888,
            options.padding,
            false,
            GuillotineStrategy()
        )
        try {
            BitmapFontWriter.setOutputFormat(BitmapFontWriter.OutputFormat.Text)
            val parameter = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = options.size
                characters = FreeTypeFontGenerator.DEFAULT_CHARS
                hinting = FreeTypeFontGenerator.Hinting.AutoMedium
                minFilter = TextureFilter.Nearest
                magFilter = TextureFilter.Nearest
                incremental = false
                packer = pixmapPacker
            }

            val fontData = generator.generateData(parameter)
            val outputDirHandle = FileHandle(outputDir)
            val pageRefs = BitmapFontWriter.writePixmaps(pixmapPacker.pages, outputDirHandle, options.baseName)
            val fontInfo = BitmapFontWriter.FontInfo(options.baseName, options.size)
            val fontFile = File(outputDir, "${options.baseName}.fnt")

            BitmapFontWriter.writeFont(
                fontData,
                pageRefs,
                FileHandle(fontFile),
                fontInfo,
                options.pageWidth,
                options.pageHeight
            )

            println("Bitmap font generated.")
            println("Input TTF: ${ttfFile.absolutePath}")
            println("Output FNT: ${fontFile.absolutePath}")
            println("Pages: ${pageRefs.size}")
            pageRefs.forEachIndexed { index, pageRef ->
                println("Page ${index + 1}: ${File(outputDir, pageRef.toString()).absolutePath}")
            }
            println("Chars: libGDX default charset (${FreeTypeFontGenerator.DEFAULT_CHARS.length} code points)")
        } finally {
            pixmapPacker.dispose()
            generator.dispose()
        }
    }

    private fun parseArgs(args: Array<String>): Options? {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            return null
        }

        var ttfPath: String? = null
        var outputDirectory: String? = null
        var baseName = "font"
        var size = 21
        var pageWidth = 256
        var pageHeight = 256
        var padding = 2

        var index = 0
        try {
            while (index < args.size) {
                when (val key = args[index]) {
                    "--ttf" -> ttfPath = readValue(args, ++index, key)
                    "--out" -> outputDirectory = readValue(args, ++index, key)
                    "--name" -> baseName = readValue(args, ++index, key)
                    "--size" -> size = readIntValue(args, ++index, key)
                    "--page-width" -> pageWidth = readIntValue(args, ++index, key)
                    "--page-height" -> pageHeight = readIntValue(args, ++index, key)
                    "--padding" -> padding = readIntValue(args, ++index, key)
                    else -> {
                        System.err.println("Unknown argument: $key")
                        return null
                    }
                }
                index++
            }
        } catch (error: IllegalArgumentException) {
            System.err.println(error.message)
            return null
        }

        if (ttfPath == null || outputDirectory == null) {
            System.err.println("Missing required arguments: --ttf and --out")
            return null
        }

        if (size <= 0 || pageWidth <= 0 || pageHeight <= 0 || padding < 0) {
            System.err.println("Invalid numeric argument. size/page dimensions must be > 0, padding must be >= 0.")
            return null
        }

        return Options(
            ttfPath = ttfPath,
            outputDirectory = outputDirectory,
            baseName = baseName,
            size = size,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            padding = padding
        )
    }

    private fun readValue(args: Array<String>, index: Int, key: String): String {
        if (index >= args.size) {
            throw IllegalArgumentException("Missing value for argument: $key")
        }
        return args[index]
    }

    private fun readIntValue(args: Array<String>, index: Int, key: String): Int {
        val value = readValue(args, index, key)
        return value.toIntOrNull()
            ?: throw IllegalArgumentException("Argument $key expects an integer, got '$value'")
    }

    private fun printUsage() {
        println("Usage:")
        println("  ./gradlew :cake:engine-tools:run --args=\"--ttf /path/font.ttf --out /path/output [options]\"")
        println()
        println("Required:")
        println("  --ttf <path>          Input TTF file")
        println("  --out <dir>           Output directory for .fnt/.png")
        println()
        println("Optional:")
        println("  --name <base>         Output base name (default: font)")
        println("  --size <px>           Font size in pixels (default: 21)")
        println("  --page-width <px>     Atlas page width (default: 256)")
        println("  --page-height <px>    Atlas page height (default: 256)")
        println("  --padding <px>        Glyph padding (default: 2)")
        println("  --help                Show this help")
        println()
        println("Charset: libGDX default charset (FreeTypeFontGenerator.DEFAULT_CHARS)")
    }

    private data class Options(
        val ttfPath: String,
        val outputDirectory: String,
        val baseName: String,
        val size: Int,
        val pageWidth: Int,
        val pageHeight: Int,
        val padding: Int
    )
}
