package com.piercingxx.txxt.core

/**
 * Serializes [BackupData] to and from the local-JSON backup format, validating
 * it on the way in. Pure Kotlin with no android.* imports and no external JSON
 * dependency, so the version gate, range gate and round-trip/no-op properties
 * are JVM-testable without a device.
 *
 * Validation on import:
 *  - **version gate** — a payload whose version differs from [BACKUP_VERSION]
 *    (e.g. written by a newer build) is refused rather than silently imported.
 *  - **range gate** — numeric fields that must be non-negative (message ids,
 *    thread ids, dates) are range-checked and rejected when out of range.
 *
 * Deserialization is deterministic, so re-importing the same payload is a
 * no-op: the same input always yields the same [BackupData].
 */
class BackupException(message: String) : IllegalArgumentException(message)

object BackupSerializer {

    /** Renders [data] to the canonical local-JSON backup string. */
    fun serialize(data: BackupData): String {
        return JsonWriter.write(toJson(data))
    }

    /** Parses and validates a backup JSON string, returning its model. */
    fun deserialize(json: String): BackupData {
        return fromTree(JsonReader(json).read())
    }

    // ---- tree -> model (with validation) ----

    private fun fromTree(tree: Json): BackupData {
        val obj = tree as? JsonObject ?: throw BackupException("backup root must be an object")
        val version = (obj.get("version") as? JsonNumber)?.toLong()
            ?: throw BackupException("backup is missing a numeric version")
        if (version != BACKUP_VERSION.toLong()) {
            throw BackupException("backup version $version is not supported (expected $BACKUP_VERSION)")
        }
        val messages = (obj.get("messages") as? JsonArray)?.items?.map { msg(it) }
            ?: throw BackupException("backup is missing a messages array")
        val settings = (obj.get("settings") as? JsonObject)?.fields
            ?.mapValues { (_, v) ->
                (v as? JsonString)?.value ?: throw BackupException("setting values must be strings")
            }
            ?: throw BackupException("backup is missing a settings object")
        val blocklist = stringList(obj.get("blocklist"), "blocklist")
        val starred = stringList(obj.get("starred"), "starred")
        return BackupData(version.toInt(), messages, settings, blocklist, starred)
    }

    private fun msg(node: Json): BackupMessage {
        val o = node as? JsonObject ?: throw BackupException("message must be an object")
        val id = rangeLong(o.get("id"), "message.id")
        val threadId = rangeLong(o.get("threadId"), "message.threadId")
        val address = (o.get("address") as? JsonString)?.value
            ?: throw BackupException("message.address must be a string")
        val body = (o.get("body") as? JsonString)?.value
            ?: throw BackupException("message.body must be a string")
        val date = rangeLong(o.get("date"), "message.date")
        return BackupMessage(id, threadId, address, body, date)
    }

    private fun rangeLong(node: Json?, field: String): Long {
        val v = (node as? JsonNumber)?.toLong() ?: throw BackupException("$field must be a number")
        if (v < 0L) throw BackupException("$field must be non-negative, was $v")
        return v
    }

    private fun stringList(node: Json?, field: String): List<String> {
        val arr = node as? JsonArray ?: throw BackupException("backup is missing a $field array")
        return arr.items.map {
            (it as? JsonString)?.value ?: throw BackupException("$field entries must be strings")
        }
    }

    // ---- model -> tree ----

    private fun toJson(data: BackupData): JsonObject {
        val obj = JsonObject()
        obj.put("version", JsonNumber(data.version.toLong()))

        val msgs = JsonArray()
        data.messages.forEach { m ->
            val o = JsonObject()
            o.put("id", JsonNumber(m.id))
            o.put("threadId", JsonNumber(m.threadId))
            o.put("address", JsonString(m.address))
            o.put("body", JsonString(m.body))
            o.put("date", JsonNumber(m.date))
            msgs.add(o)
        }
        obj.put("messages", msgs)

        val settings = JsonObject()
        data.settings.forEach { (k, v) -> settings.put(k, JsonString(v)) }
        obj.put("settings", settings)

        obj.put("blocklist", JsonArray().also { a -> data.blocklist.forEach { a.add(JsonString(it)) } })
        obj.put("starred", JsonArray().also { a -> data.starred.forEach { a.add(JsonString(it)) } })
        return obj
    }
}

// ---- minimal JSON tree + reader/writer (self-contained, no external dep) ----

private sealed class Json
private class JsonObject : Json() {
    val fields = LinkedHashMap<String, Json>()
    fun get(key: String): Json? = fields[key]
    fun put(key: String, value: Json) { fields[key] = value }
}
private class JsonArray : Json() {
    val items = ArrayList<Json>()
    fun add(value: Json) { items.add(value) }
}
private class JsonString(val value: String) : Json()
private class JsonNumber(private val raw: Long) : Json() {
    fun toLong(): Long = raw
}
private class JsonBool(val value: Boolean) : Json()
private object JsonNull : Json()

private object JsonWriter {
    fun write(node: Json): String {
        val sb = StringBuilder()
        writeNode(sb, node)
        return sb.toString()
    }

    private fun writeNode(sb: StringBuilder, node: Json) {
        when (node) {
            is JsonObject -> {
                sb.append('{')
                var first = true
                node.fields.forEach { (k, v) ->
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k)
                    sb.append(':')
                    writeNode(sb, v)
                }
                sb.append('}')
            }
            is JsonArray -> {
                sb.append('[')
                var first = true
                node.items.forEach { v ->
                    if (!first) sb.append(',')
                    first = false
                    writeNode(sb, v)
                }
                sb.append(']')
            }
            is JsonString -> writeString(sb, node.value)
            is JsonNumber -> sb.append(node.toLong())
            is JsonBool -> sb.append(if (node.value) "true" else "false")
            JsonNull -> sb.append("null")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        s.forEach { c ->
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}

private class JsonReader(private val src: String) {
    private var pos = 0

    fun read(): Json {
        val v = readValue()
        skipWs()
        if (pos != src.length) throw BackupException("trailing characters after JSON value")
        return v
    }

    private fun readValue(): Json {
        skipWs()
        if (pos >= src.length) throw BackupException("unexpected end of JSON")
        return when (val c = src[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> JsonString(readString())
            't' -> { expect("true"); JsonBool(true) }
            'f' -> { expect("false"); JsonBool(false) }
            'n' -> { expect("null"); JsonNull }
            else -> if (c == '-' || c in '0'..'9') readNumber()
            else throw BackupException("unexpected character '$c' in JSON")
        }
    }

    private fun readObject(): JsonObject {
        pos++ // '{'
        val obj = JsonObject()
        skipWs()
        if (pos < src.length && src[pos] == '}') { pos++; return obj }
        while (true) {
            skipWs()
            if (pos >= src.length || src[pos] != '"') throw BackupException("expected string key in object")
            val key = readString()
            skipWs()
            if (pos >= src.length || src[pos] != ':') throw BackupException("expected ':' after key")
            pos++
            obj.put(key, readValue())
            skipWs()
            if (pos >= src.length) throw BackupException("unterminated object")
            when (src[pos]) {
                ',' -> pos++
                '}' -> { pos++; return obj }
                else -> throw BackupException("expected ',' or '}' in object")
            }
        }
    }

    private fun readArray(): JsonArray {
        pos++ // '['
        val arr = JsonArray()
        skipWs()
        if (pos < src.length && src[pos] == ']') { pos++; return arr }
        while (true) {
            arr.add(readValue())
            skipWs()
            if (pos >= src.length) throw BackupException("unterminated array")
            when (src[pos]) {
                ',' -> pos++
                ']' -> { pos++; return arr }
                else -> throw BackupException("expected ',' or ']' in array")
            }
        }
    }

    private fun readString(): String {
        pos++ // opening quote
        val sb = StringBuilder()
        while (true) {
            if (pos >= src.length) throw BackupException("unterminated string")
            val c = src[pos++]
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> {
                    if (pos >= src.length) throw BackupException("unterminated escape")
                    when (val e = src[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > src.length) throw BackupException("invalid \\u escape")
                            val code = src.substring(pos, pos + 4).toIntOrNull(16)
                                ?: throw BackupException("invalid \\u escape")
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> throw BackupException("invalid escape '\\$e'")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun readNumber(): JsonNumber {
        val start = pos
        if (pos < src.length && src[pos] == '-') pos++
        var hasDigit = false
        while (pos < src.length && src[pos] in '0'..'9') { hasDigit = true; pos++ }
        if (pos < src.length && src[pos] == '.') {
            pos++
            while (pos < src.length && src[pos] in '0'..'9') { hasDigit = true; pos++ }
        }
        if (!hasDigit) throw BackupException("invalid number")
        val value = src.substring(start, pos).toLongOrNull()
            ?: throw BackupException("number out of range: ${src.substring(start, pos)}")
        return JsonNumber(value)
    }

    private fun expect(word: String) {
        if (pos + word.length > src.length || src.substring(pos, pos + word.length) != word) {
            throw BackupException("invalid literal")
        }
        pos += word.length
    }

    private fun skipWs() {
        while (pos < src.length && (src[pos] == ' ' || src[pos] == '\t' || src[pos] == '\n' || src[pos] == '\r')) pos++
    }
}