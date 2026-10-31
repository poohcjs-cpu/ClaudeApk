package io.github.poohcjs.hotdealradar.data

import android.content.Context
import io.github.poohcjs.hotdealradar.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

val JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

/**
 * GitHub Pages에 올라간 deals.json을 받아 온다.
 * 받은 원본은 파일로 남겨 두어 인터넷이 끊겨도 마지막 내용을 보여 준다.
 */
class Repository(private val context: Context, private val prefs: Prefs) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val cacheFile get() = File(context.filesDir, "deals.json")

    fun dataUrl(): String = prefs.settings.value.dataUrl.trim().ifEmpty { BuildConfig.DEFAULT_DATA_URL }

    suspend fun loadCached(): DealsFile? = withContext(Dispatchers.IO) {
        cacheFile.takeIf { it.exists() }?.let { f -> runCatching { parse(f.readText()) }.getOrNull() }
    }

    /** 새로 받기. 실패하면 IOException. */
    suspend fun fetch(): DealsFile = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(dataUrl())
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", "HotdealRadar-Android/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val text = resp.body.string()
            val data = runCatching { parse(text) }.getOrElse { throw IOException("데이터 형식을 읽지 못했어요", it) }
            val tmp = File(context.filesDir, "deals.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(cacheFile)
            data
        }
    }

    companion object {
        fun parse(text: String): DealsFile = JSON.decodeFromString(DealsFile.serializer(), text)
    }
}
