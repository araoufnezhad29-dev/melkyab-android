package ir.melkyab.app

import android.text.Html
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class DivarOnlineAd(
    val title: String,
    val summary: String,
    val price: String,
    val imageUrl: String,
    val url: String
)

object DivarOnlineSearch {
    fun search(categoryPath: String, query: String, filters: Map<String, String> = emptyMap()): List<DivarOnlineAd> {
        val params = linkedMapOf<String, String>()
        if (query.isNotBlank()) params["q"] = query
        filters.forEach { (key, value) -> if (value.isNotBlank()) params[key] = value }
        val suffix = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val address = "https://divar.ir/s/tehran/$categoryPath" + if (suffix.isBlank()) "" else "?$suffix"
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.7")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parse(html)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(html: String): List<DivarOnlineAd> {
        val results = LinkedHashMap<String, DivarOnlineAd>()
        val anchor = Regex("""(?is)<a\\b[^>]*href=["']([^"']*/v/[^"']+)["'][^>]*>(.*?)</a>""")
        val imagePattern = Regex("""(?is)<img\\b[^>]*(?:src|data-src)=["']([^"']+)["'][^>]*>""")
        for (match in anchor.findAll(html)) {
            val rawUrl = match.groupValues[1]
            val tokenMatch = Regex("""/v/(?:[^/?#]+/)?([A-Za-z0-9_-]{7,20})(?:[?#]|$)""").find(rawUrl) ?: continue
            val token = tokenMatch.groupValues[1]
            if (results.containsKey(token)) continue
            val inner = match.groupValues[2]
            val plain = Html.fromHtml(inner.replace(Regex("""(?is)<(script|style)[^>]*>.*?</\\1>"""), " "), Html.FROM_HTML_MODE_LEGACY)
                .toString().replace('\\u200c', ' ').replace(Regex("""\\s+"""), " ").trim()
            if (plain.length < 5) continue
            val priceMatch = Regex("""[۰-۹0-9][۰-۹0-9,٬.٫]*\\s*(?:تومان|میلیارد|میلیون|توافقی)""").find(plain)
            val price = priceMatch?.value?.trim().orEmpty()
            val title = (plain.substringBefore(price).ifBlank { plain }).trim().take(110)
            val image = imagePattern.find(inner)?.groupValues?.get(1)?.let {
                if (it.startsWith("//")) "https:$it" else it
            }?.takeIf { it.startsWith("https://") && !it.startsWith("data:") }.orEmpty()
            val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "https://divar.ir" + rawUrl
            val summary = plain.removePrefix(title).trim().take(120)
            results[token] = DivarOnlineAd(title.ifBlank { "آگهی دیوار" }, summary, price, image, fullUrl)
            if (results.size >= 40) break
        }
        return results.values.toList()
    }
}
