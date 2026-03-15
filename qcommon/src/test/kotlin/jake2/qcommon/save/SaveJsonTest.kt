package jake2.qcommon.save

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SaveJsonTest {
    @Test
    fun mapperRoundTripsJakeOwnedSnapshotDto() {
        val mapper = SaveJson.mapper()
        val original = TestSnapshot(
            schemaVersion = 1,
            map = "base1",
            autosave = true,
        )

        val json = mapper.writeValueAsString(original)
        val restored: TestSnapshot = mapper.readValue(json)

        assertEquals(original, restored)
    }

    private data class TestSnapshot(
        val schemaVersion: Int,
        val map: String,
        val autosave: Boolean,
    )
}
