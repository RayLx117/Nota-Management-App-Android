package dev.ray.reminders.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * "Find a picture": searches Openverse (openly licensed photos, no account or key needed).
 * This is the only part of Nota that uses the internet, and only when the button is tapped.
 */
object ImageSearch {
    data class Result(val id: String, val thumb: String, val full: String, val title: String, val creator: String, val license: String)

    private const val AGENT = "Nota/1.0 (Android app; https://github.com/RayLx117/RayCode)"

    /** Where to look: open photos, Wikipedia article pictures, or anime/manga art. */
    enum class Source(val label: String) { PHOTOS("Photos"), WIKIPEDIA("Wikipedia"), ANIME("Anime") }

    private fun get(url: String, maxBytes: Int = 8_000_000, postJson: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", AGENT)
        if (postJson != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(postJson.toByteArray()) }
        }
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16_384)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) error("too big")
                }
                out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }

    /** Up to [count] pictures for [query] from [source]. Call off the main thread; throws when offline. */
    fun search(query: String, source: Source = Source.PHOTOS, count: Int = 18): List<Result> = when (source) {
        Source.PHOTOS -> photos(query, count)
        Source.WIKIPEDIA -> wikipedia(query, count)
        Source.ANIME -> anime(query, count)
    }

    private fun photos(query: String, count: Int): List<Result> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val body = get("https://api.openverse.org/v1/images/?q=$q&page_size=$count&mature=false").decodeToString()
        val results = Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            val id = s("id")
            val full = s("url")
            if (id.isBlank() || full.isBlank()) return@mapNotNull null
            Result(
                id = id, thumb = s("thumbnail").ifBlank { "https://api.openverse.org/v1/images/$id/thumb/" },
                full = full, title = s("title"), creator = s("creator"), license = s("license").uppercase(),
            )
        }
    }

    /** The lead picture of each matching Wikipedia article. */
    private fun wikipedia(query: String, count: Int): List<Result> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val body = get(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=search" +
                "&gsrsearch=$q&gsrlimit=$count&prop=pageimages&piprop=thumbnail%7Coriginal&pithumbsize=400",
        ).decodeToString()
        val pages = Json.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("pages")?.jsonArray ?: return emptyList()
        return pages.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val thumb = o["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val full = o["original"]?.jsonObject?.get("source")?.jsonPrimitive?.contentOrNull ?: thumb
            // Skip SVG logos/diagrams; they don't decode as photos.
            if (full.endsWith(".svg", true)) return@mapNotNull null
            val index = o["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 999
            index to Result(
                id = "w" + (o["pageid"]?.jsonPrimitive?.contentOrNull ?: thumb), thumb = thumb, full = full,
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), creator = "Wikipedia", license = "",
            )
        }.sortedBy { it.first }.map { it.second }
    }

    /** Anime and manga covers (and wide banners, when a title has one) from AniList. */
    private fun anime(query: String, count: Int): List<Result> {
        val gql = "query(\$s:String){Page(perPage:$count){media(search:\$s,isAdult:false){id title{romaji english} coverImage{large extraLarge} bannerImage}}}"
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("query", kotlinx.serialization.json.JsonPrimitive(gql))
            put("variables", kotlinx.serialization.json.buildJsonObject { put("s", kotlinx.serialization.json.JsonPrimitive(query.trim())) })
        }.toString()
        val body = get("https://graphql.anilist.co", postJson = payload).decodeToString()
        val media = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
            ?: return emptyList()
        return media.flatMap { el ->
            val o = el as? JsonObject ?: return@flatMap emptyList()
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@flatMap emptyList()
            val t = o["title"]?.jsonObject
            val title = t?.get("english")?.jsonPrimitive?.contentOrNull ?: t?.get("romaji")?.jsonPrimitive?.contentOrNull.orEmpty()
            val cover = o["coverImage"]?.jsonObject
            val large = cover?.get("large")?.jsonPrimitive?.contentOrNull
            val xl = cover?.get("extraLarge")?.jsonPrimitive?.contentOrNull ?: large
            val banner = o["bannerImage"]?.jsonPrimitive?.contentOrNull
            listOfNotNull(
                if (large != null && xl != null) Result("a$id", large, xl, title, "AniList", "") else null,
                if (banner != null) Result("b$id", banner, banner, "$title (banner)", "AniList", "") else null,
            )
        }
    }

    /** A small preview for the results grid. Off the main thread. */
    fun thumbnail(url: String): Bitmap? = runCatching {
        val bytes = get(url, 3_000_000)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    /** Downloads the chosen photo (falling back to its preview) into Nota's pictures; returns its path. */
    fun save(ctx: Context, r: Result): String? {
        for (url in listOf(r.full, r.thumb)) {
            val tmp = File(ctx.cacheDir, "download.img")
            val ok = runCatching { tmp.writeBytes(get(url)) }.isSuccess
            if (!ok) continue
            val path = Images.importFile(ctx, tmp)
            tmp.delete()
            if (path != null) return path
        }
        return null
    }
}
