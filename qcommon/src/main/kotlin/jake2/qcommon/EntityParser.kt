package jake2.qcommon

/**
 * Parse the entities string.
 * Very similar to the json format, but without a colon between key/value pairs
 * and without commas between objects, like
 *
 *    {
 *     "key" "value"
 *    }
 *    {
 *     "foo" "bar"
 *    }
 *
 * TODO: add some validation and error messages, because right now parser just silently skips anything it doesn't like
 */
fun parseEntities(src: String?): List<Map<String, String>> {
    if (src.isNullOrBlank())
        return emptyList()

    var currentEntity: MutableMap<String, String> = HashMap() // initial value not used
    val result = mutableListOf<MutableMap<String, String>>()
    var currentString: StringBuilder? = null // string between the double quotes "

    var key: String? = null
    var value: String?

    var startComment = false // first slash
    var inComment = false

    src.forEach { c ->
        when {
            inComment -> {
                if (c == '\n')
                    inComment = false
                // else -> skip
            }
            c == '/' && currentString == null -> {
                if (startComment) {
                    inComment = true
                    startComment = false
                }
                else
                    startComment = true
            }
            c == '"' -> {
                if (currentString != null) {
                    // either key or value is completed
                    if (key == null) {
                        key = currentString.toString()
                    } else {
                        value = currentString.toString()
                        currentEntity[key!!] = value!!
                        // cleanup
                        key = null
                        value = null
                    }
                    currentString = null
                } else {
                    currentString = StringBuilder()
                }
            }
            c == '{' && currentString == null -> {
                currentEntity = HashMap()
            }
            c == '}' && currentString == null -> {
                result.add(currentEntity)
            }
            currentString != null -> {
                currentString?.append(c)
            }
            // else -> skip
        }
    }
    return result
}
