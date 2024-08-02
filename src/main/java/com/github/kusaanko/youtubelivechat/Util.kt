package com.github.kusaanko.youtubelivechat

import com.google.gson.Gson
import com.google.gson.JsonElement
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

object Util {
    private var gson = Gson()

    fun toJSON(json: Map<String, Any?>): String {
        val js = StringBuilder()
        js.append("{")
        for (key in json.keys) {
            js.append("'").append(key).append("': ")
            when (val d = json[key]) {
                is Byte, is Char, is Short, is Int, is Long, is Float, is Double, is Boolean -> js.append(d)
                is Map<*, *> -> js.append(toJSON(d as Map<String, Any?>))
                else -> js.append("\"").append(d.toString().replace("\"", "\\\"").replace("\\", "\\\\")).append("\"")
            }
            js.append(", ")
        }
        return js.substring(0, js.length - 2) + "}"
    }

    @JvmStatic
    fun toJSON(json: String): Map<String, Any> {
        require(json.startsWith("{")) { "This is not json(map)!" }
        val result: Map<String, Any> = gson.fromJson<Map<String, Any>>(
            json,
            MutableMap::class.java
        )
        return result
    }

    fun getJSONMap(json: Map<String, Any>, vararg keys: String): Map<String, Any> {
        var map = json
        for (key in keys) {
            if (map.containsKey(key)) {
                map = map[key] as Map<String, Any>
            } else {
                return emptyMap()
            }
        }
        return map
    }

    fun getJSONMap(json: Map<String, *>?, vararg keys: Any): Map<String, *>? {
        var map = json
        var list: List<*>? = null
        for (key in keys) {
            if (map != null) {
                if (map.containsKey(key.toString())) {
                    val value = map[key.toString()]
                    if (value is List<*>) {
                        list = value
                        map = null
                    } else {
                        map = value as Map<String, *>
                    }
                } else {
                    return null
                }
            } else {
                map = list!![key as Int] as Map<String, *>
                list = null
            }
        }
        return map
    }

    fun getJSONList(json: Map<String, Any>, listKey: String, vararg keys: String): List<Any>? {
        val map = getJSONMap(json, *keys)
        if (map != null && map.containsKey(listKey)) {
            return map[listKey] as List<Any>
        }
        return null
    }

    fun getJSONValue(json: Map<String, Any>, key: String): Any? {
        if (json != null && json.containsKey(key)) {
            return json[key]
        }
        return null
    }

    fun getJSONValueString(json: Map<String, Any>, key: String): String? {
        val value = getJSONValue(json, key)
        if (value != null) {
            return value.toString()
        }
        return null
    }

    fun getJSONValueBoolean(json: Map<String, Any>, key: String): Boolean {
        val value = getJSONValue(json, key)
        if (value != null) {
            return value as Boolean
        }
        return false
    }

    fun getJSONValueLong(json: Map<String, Any>, key: String): Long {
        val value = getJSONValue(json, key)
        if (value != null) {
            return (value as Double).toLong()
        }
        return 0
    }

    fun getJSONValueInt(json: Map<String, Any>, key: String): Int {
        return getJSONValueLong(json, key).toInt()
    }

    @Throws(IOException::class)
    fun getPageContent(url: String, header: MutableMap<String, String>): String? {
        val u = URL(url)
        val connection = u.openConnection() as HttpURLConnection
        putRequestHeader(header)
        for (key in header.keys) {
            connection.setRequestProperty(key, header[key])
        }
        connection.connect()
        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                val inputStream = connection.inputStream
                val buff = ByteArray(8192)
                var len: Int
                val baos = ByteArrayOutputStream()
                while ((inputStream.read(buff).also { len = it }) != -1) {
                    baos.write(buff, 0, len)
                }
                inputStream.close()
                val content = baos.toString(StandardCharsets.UTF_8.toString())
                baos.close()
                return content
            }
        } catch (exception: IOException) {
            throw IOException("Error during http request ", exception)
        }
        return null
    }

    @Throws(IOException::class)
    fun getPageContentWithJson(url: String, data: String, header: MutableMap<String, String>): String? {
        val u = URL(url)
        val connection = u.openConnection() as HttpURLConnection
        putRequestHeader(header)
        header["Content-Type"] = "application/json"
        header["Content-Length"] = data.length.toString()
        for (key in header.keys) {
            connection.setRequestProperty(key, header[key])
        }
        connection.requestMethod = "POST"
        connection.doOutput = true
        val writer = OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8)
        writer.write(data)
        writer.close()
        connection.connect()
        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                val inputStream = connection.inputStream
                val buff = ByteArray(8192)
                var len: Int
                val baos = ByteArrayOutputStream()
                while ((inputStream.read(buff).also { len = it }) != -1) {
                    baos.write(buff, 0, len)
                }
                inputStream.close()
                val content = baos.toString(StandardCharsets.UTF_8.toString())
                baos.close()
                return content
            }
        } catch (exception: IOException) {
            throw IOException("Error during http request ", exception)
        }
        return null
    }

    @Throws(IOException::class)
    fun sendHttpRequestWithJson(url: String, data: String, header: MutableMap<String, String>) {
        val u = URL(url)
        val connection = u.openConnection() as HttpURLConnection
        putRequestHeader(header)
        header["Content-Type"] = "application/json"
        header["Content-Length"] = data.length.toString()
        for (key in header.keys) {
            connection.setRequestProperty(key, header[key])
        }
        connection.requestMethod = "POST"
        connection.doOutput = true
        val writer = OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8)
        writer.write(data)
        writer.close()
        connection.connect()
        try {
            connection.inputStream
            connection.disconnect()
        } catch (e: IOException) {
            val reader = BufferedReader(InputStreamReader(connection.errorStream))
            var s: String
            val str = StringBuilder()
            while ((reader.readLine().also { s = it }) != null) {
                str.append(s)
            }
            connection.disconnect()
            throw IOException(str.toString(), e)
        }
    }

    private fun putRequestHeader(header: MutableMap<String, String>) {
        header["Accept-Charset"] = "utf-8"
        header["User-Agent"] = YouTubeLiveChat.userAgent
    }

    @JvmStatic
    fun generateClientMessageId(): String {
        val base = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-"
        val sb = StringBuilder()
        val random = Random()

        for (i in 0..25) {
            sb.append(base[random.nextInt(base.length)])
        }

        return sb.toString()
    }

    @JvmStatic
    fun searchJsonElementByKey(key: String, jsonElement: JsonElement): JsonElement? {
        var value: JsonElement? = null

        // If input is an array, iterate through each element
        if (jsonElement.isJsonArray) {
            for (jsonElement1 in jsonElement.asJsonArray) {
                value = searchJsonElementByKey(key, jsonElement1)
                if (value != null) {
                    return value
                }
            }
        } else {
            // If input is object, iterate through the keys
            if (jsonElement.isJsonObject) {
                val entrySet = jsonElement
                    .asJsonObject.entrySet()
                for ((key1, value1) in entrySet) {
                    // If key corresponds to the

                    if (key1 == key) {
                        value = value1
                        return value
                    }

                    // Use the entry as input, recursively
                    value = searchJsonElementByKey(key, value1)
                    if (value != null) {
                        return value
                    }
                }
            } else {
                if (jsonElement.toString() == key) {
                    value = jsonElement
                    return value
                }
            }
        }
        return value
    }
}
