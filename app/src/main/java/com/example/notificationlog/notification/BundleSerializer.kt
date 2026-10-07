package com.example.notificationlog.notification

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcelable
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.security.MessageDigest

object BundleSerializer {
    private const val MAX_BYTES = 1024 * 1024

    fun serialize(bundle: Bundle?): String {
        val root = JSONObject()
        if (bundle == null) return root.toString()
        for (key in bundle.keySet().sorted()) {
            runCatching { root.put(key, value(bundle.get(key))) }
        }
        val result = root.toString()
        return if (result.toByteArray().size <= MAX_BYTES) result
        else result.take(MAX_BYTES).let { "{\"__truncated\":true,\"data\":${JSONObject.quote(it)}}" }
    }

    private fun value(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Bundle -> JSONObject().also { o -> v.keySet().sorted().forEach { k -> runCatching { o.put(k, value(v.get(k))) } } }
        is String, is Number, is Boolean -> v
        is CharSequence -> v.toString()
        is Bitmap -> JSONObject().apply { put("type", "Bitmap"); put("width", v.width); put("height", v.height); put("config", v.config?.name ?: JSONObject.NULL) }
        is Parcelable -> JSONObject().apply { put("type", v.javaClass.name); put("value", v.toString()) }
        is Serializable -> JSONObject().apply { put("type", v.javaClass.name); put("value", v.toString()) }
        is Array<*> -> JSONArray().also { a -> v.forEach { a.put(value(it)) } }
        is IntArray -> JSONArray().also { a -> v.forEach { a.put(it) } }
        is LongArray -> JSONArray().also { a -> v.forEach { a.put(it) } }
        is BooleanArray -> JSONArray().also { a -> v.forEach { a.put(it) } }
        is ByteArray -> JSONObject().apply { put("type", "ByteArray"); put("size", v.size); put("sha256", sha256(v)) }
        else -> v.toString()
    }

    fun sha256(text: String): String = sha256(text.toByteArray())
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
