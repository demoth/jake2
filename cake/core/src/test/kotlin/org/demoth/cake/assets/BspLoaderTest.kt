package org.demoth.cake.assets

import jake2.qcommon.Defines
import jake2.qcommon.filesystem.Bsp
import jake2.qcommon.filesystem.IDBSPHEADER
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BspLoaderTest {

    @Test
    fun collectWalTexturePathsSkipsSkyAndDeduplicates() {
        val bspData = minimalBspWithTextures(
            textureNames = listOf("floor", "skybox", "floor", " ", "lava")
        )

        val paths = collectWalTexturePaths(bspData)

        assertEquals(
            listOf(
                "textures/floor.wal",
                "textures/lava.wal",
            ),
            paths
        )
    }

    @Test
    fun collectWalTexturePathsIncludesAnimationChainFrames() {
        val bspData = minimalBspWithTextures(
            textureNames = listOf("comp_a", "comp_b", "comp_c"),
            texInfoNextIndices = listOf(1, 2, 0),
            faceTextureInfoIndices = listOf(0)
        )

        val paths = collectWalTexturePaths(bspData)

        assertEquals(
            listOf(
                "textures/comp_a.wal",
                "textures/comp_b.wal",
                "textures/comp_c.wal",
            ),
            paths
        )
    }

    @Test
    fun buildWorldRenderDataMapsLeavesToWorldSurfaces() {
        val bspData = minimalBspWithTextures(
            textureNames = listOf("floor", "skybox", "lava"),
            leafFaceIndices = listOf(0, 1, 2)
        )
        val bsp = Bsp(ByteBuffer.wrap(bspData))

        val surfaces = collectWorldSurfaceRecords(bsp)
        val world = buildWorldRenderData(bsp, surfaces)

        assertEquals(2, world.surfaces.size) // sky face is filtered out
        assertEquals(1, world.leaves.size)
        assertEquals(intArrayOf(0, 1).toList(), world.leaves.first().surfaceIndices.toList())
        assertEquals(listOf("floor", "lava"), world.textureInfos.map { it.textureName })
    }

    @Test
    fun collectInlineModelRenderDataBuildsStableFaceParts() {
        val bspData = minimalBspWithTextures(
            textureNames = listOf("world", "door_a", "door_b"),
            texInfoNextIndices = listOf(0, 2, 1),
            faceTextureInfoIndices = listOf(0, 1, 2),
            modelFaceRanges = listOf(
                0 to 1, // world model (face 0)
                1 to 2, // inline model 1 (faces 1 and 2)
            )
        )
        val bsp = Bsp(ByteBuffer.wrap(bspData))

        val inline = collectInlineModelRenderData(bsp)

        assertEquals(1, inline.size)
        assertEquals(1, inline.first().modelIndex)
        assertEquals(listOf(1, 2), inline.first().parts.map { it.textureInfoIndex })
        assertEquals(
            listOf("inline_1_face_1", "inline_1_face_2"),
            inline.first().parts.map { it.meshPartId }
        )
        assertEquals(listOf(2, 1), inline.first().parts.map { it.textureAnimationNext })
    }

    @Test
    fun collectWorldSurfaceRecordsExtractsPrimaryLightStyleIndex() {
        val bspData = minimalBspWithTextures(
            textureNames = listOf("floor"),
            faceLightStyles = listOf(byteArrayOf(7, (-1).toByte(), (-1).toByte(), (-1).toByte())),
        )
        val bsp = Bsp(ByteBuffer.wrap(bspData))

        val surfaces = collectWorldSurfaceRecords(bsp)

        assertEquals(1, surfaces.size)
        assertEquals(7, surfaces.first().primaryLightStyleIndex)
    }

    @Test
    fun collectInlineModelRenderDataExtractsFaceLightStyleMetadata() {
        val bspData = minimalBspWithTextures(
            textureNames = listOf("world", "door"),
            faceTextureInfoIndices = listOf(0, 1),
            faceLightStyles = listOf(
                byteArrayOf(0, (-1).toByte(), (-1).toByte(), (-1).toByte()),
                byteArrayOf(9, 11, (-1).toByte(), (-1).toByte()),
            ),
            modelFaceRanges = listOf(
                0 to 1, // world
                1 to 1, // inline model 1
            ),
        )
        val bsp = Bsp(ByteBuffer.wrap(bspData))

        val inline = collectInlineModelRenderData(bsp)

        assertEquals(1, inline.size)
        val part = inline.first().parts.single()
        assertEquals(9, part.primaryLightStyleIndex)
        assertEquals(listOf(9, 11), part.lightMapStyles.map { it.toInt() and 0xFF }.takeWhile { it != 255 })
    }

    private fun minimalBspWithTextures(
        textureNames: List<String>,
        texInfoNextIndices: List<Int> = List(textureNames.size) { 0 },
        faceTextureInfoIndices: List<Int> = textureNames.indices.toList(),
        faceLightStyles: List<ByteArray> = List(faceTextureInfoIndices.size) { byteArrayOf(0, 0, 0, 0) },
        leafFaceIndices: List<Int> = emptyList(),
        modelFaceRanges: List<Pair<Int, Int>>? = null,
    ): ByteArray {
        check(texInfoNextIndices.size == textureNames.size) {
            "texInfoNextIndices must match textureNames size"
        }
        check(faceLightStyles.size == faceTextureInfoIndices.size) {
            "faceLightStyles must match faceTextureInfoIndices size"
        }
        val faceCount = faceTextureInfoIndices.size
        val resolvedModelFaceRanges = modelFaceRanges ?: listOf(0 to faceCount)
        val facesData = ByteBuffer.allocate(faceCount * 20)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                faceTextureInfoIndices.forEachIndexed { index, textureInfoIndex ->
                    putShort(0) // plane
                    putShort(0) // plane side
                    putInt(0) // first edge index
                    putShort(0) // num edges
                    putShort(textureInfoIndex.toShort()) // texture info index
                    put(faceLightStyles[index].copyOf(4)) // light styles
                    putInt(0) // light map offset
                }
            }
            .array()

        val texturesData = ByteBuffer.allocate(textureNames.size * 76)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                textureNames.forEachIndexed { textureIndex, textureName ->
                    repeat(8) { putFloat(0f) } // uAxis + uOffset + vAxis + vOffset
                    putInt(0) // flags
                    putInt(0) // value
                    val nameBytes = ByteArray(32)
                    val rawNameBytes = textureName.toByteArray(Charsets.US_ASCII)
                    val copyLen = minOf(rawNameBytes.size, nameBytes.size)
                    System.arraycopy(rawNameBytes, 0, nameBytes, 0, copyLen)
                    put(nameBytes)
                    putInt(texInfoNextIndices[textureIndex]) // next
                }
            }
            .array()

        val leafFacesData = ByteBuffer.allocate(leafFaceIndices.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                leafFaceIndices.forEach { faceIndex ->
                    putShort(faceIndex.toShort())
                }
            }
            .array()

        val leavesData = if (leafFaceIndices.isEmpty()) {
            ByteArray(0)
        } else {
            ByteBuffer.allocate(28)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putInt(0) // contents
                    putShort(0) // cluster
                    putShort(0) // area
                    repeat(6) { putShort(0) } // mins/maxs
                    putShort(0) // firstLeafFace
                    putShort(leafFaceIndices.size.toShort()) // numLeafFaces
                    putShort(0) // firstLeafBrush
                    putShort(0) // numLeafBrushes
                }
                .array()
        }

        val modelsData = ByteBuffer.allocate(48 * resolvedModelFaceRanges.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                resolvedModelFaceRanges.forEach { (firstFace, modelFaceCount) ->
                    repeat(9) { putFloat(0f) } // mins + maxs + origin
                    putInt(0) // headNode
                    putInt(firstFace) // firstFace
                    putInt(modelFaceCount) // faceCount
                }
            }
            .array()

        val headerSize = 8 + Defines.HEADER_LUMPS * 8
        val lumpOffsets = IntArray(Defines.HEADER_LUMPS) { 0 }
        val lumpLengths = IntArray(Defines.HEADER_LUMPS) { 0 }

        var cursor = headerSize
        // LUMP_TEXINFO
        lumpOffsets[5] = cursor
        lumpLengths[5] = texturesData.size
        cursor += texturesData.size
        // LUMP_FACES
        lumpOffsets[6] = cursor
        lumpLengths[6] = facesData.size
        cursor += facesData.size
        // LUMP_LEAFS
        lumpOffsets[8] = cursor
        lumpLengths[8] = leavesData.size
        cursor += leavesData.size
        // LUMP_LEAFFACES
        lumpOffsets[9] = cursor
        lumpLengths[9] = leafFacesData.size
        cursor += leafFacesData.size
        // LUMP_MODELS
        lumpOffsets[13] = cursor
        lumpLengths[13] = modelsData.size
        cursor += modelsData.size

        val result = ByteBuffer.allocate(cursor).order(ByteOrder.LITTLE_ENDIAN)
        result.putInt(IDBSPHEADER)
        result.putInt(38) // Quake2 BSP version

        for (i in 0 until Defines.HEADER_LUMPS) {
            result.putInt(lumpOffsets[i])
            result.putInt(lumpLengths[i])
        }

        result.position(lumpOffsets[5])
        result.put(texturesData)
        result.position(lumpOffsets[6])
        result.put(facesData)
        result.position(lumpOffsets[8])
        result.put(leavesData)
        result.position(lumpOffsets[9])
        result.put(leafFacesData)
        result.position(lumpOffsets[13])
        result.put(modelsData)
        return result.array()
    }
}
