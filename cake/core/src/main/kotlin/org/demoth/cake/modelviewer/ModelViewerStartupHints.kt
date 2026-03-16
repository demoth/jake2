package org.demoth.cake.modelviewer

import java.nio.file.Files
import java.nio.file.Path

internal data class ModelViewerStartupHints(
    val basedir: String? = null,
    val gamemod: String? = null,
)

internal fun detectModelViewerStartupHints(
    openedFilePath: String,
    currentDirectory: Path = Path.of("").toAbsolutePath().normalize(),
): ModelViewerStartupHints {
    val openedFile = Path.of(openedFilePath).toAbsolutePath().normalize()
    val basedir = detectBasedirCandidate(currentDirectory) ?: detectBasedirCandidate(openedFile.parent)
    val gamemod = basedir?.let { detectGameMod(openedFile, it) }
    return ModelViewerStartupHints(
        basedir = basedir?.toString(),
        gamemod = gamemod,
    )
}

private fun detectBasedirCandidate(start: Path?): Path? {
    var current = start
    while (current != null) {
        if (current.fileName?.toString()?.equals("baseq2", ignoreCase = true) == true) {
            return current.parent?.toAbsolutePath()?.normalize()
        }
        if (Files.isDirectory(current.resolve("baseq2"))) {
            return current.toAbsolutePath().normalize()
        }
        current = current.parent
    }
    return null
}

private fun detectGameMod(openedFile: Path, basedir: Path): String? {
    if (!openedFile.startsWith(basedir)) {
        return null
    }
    val relativeParent = basedir.relativize(openedFile.parent ?: return null)
    if (relativeParent.nameCount == 0) {
        return null
    }
    val root = relativeParent.getName(0).toString()
    return root.takeUnless { it.equals("baseq2", ignoreCase = true) }
}
