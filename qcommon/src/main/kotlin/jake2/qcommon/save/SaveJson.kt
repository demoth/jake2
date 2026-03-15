package jake2.qcommon.save

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.InputStream
import java.io.OutputStream

/**
 * Shared JSON mapper factory for Jake2-owned persistence formats.
 *
 * Persistence code should depend on explicit snapshot DTOs rather than
 * attempting to serialize live runtime graphs directly.
 */
object SaveJson {
    @JvmStatic
    fun mapper(): ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    @JvmStatic
    fun write(output: OutputStream, value: Any) {
        mapper().writeValue(output, value)
    }

    @JvmStatic
    fun <T> read(input: InputStream, type: Class<T>): T = mapper().readValue(input, type)
}
