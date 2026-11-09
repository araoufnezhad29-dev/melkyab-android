package ir.melkyab.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SheypoorOnlineSearch {
    fun search(query: String): List<DivarOnlineAd> {
        val params = buildString {
            append("o=n")
            if (query.isNotBlank()) append("&q=").append(URLEncoder.encode(query, "UTF-8"))
        }
        val address = "https://www.sheypoor.com/api/v10.0.0/search/tehran/real-estate?$params"
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 22000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "application/vnd.api+json")
            setRequestProperty("Content-Type", "application/vnd.api+json")
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9")
            setRequestProperty("Referer", "https://www.sheypoor.com/")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val root = connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            val blocks = root.optJSONArray("data") ?: return emptyList()
            val output = LinkedHashMap<String, DivarOnlineAd>()
            for (i in 0 until blocks.length()) {
                val block = blocks.optJSONObject(i) ?: continue
                val type = block.optString("type")
                val cards = when (type) {
                    "special", "vip" -> block.optJSONArray("items") ?: JSONArray()
                    "normal", "paidEngagement", "nearby" -> JSONArray().put(block)
                    else -> JSONArray()
                }
                for (j in 0 until cards.length()) {
                    val item = cards.optJSONObject(j) ?: continue
                    val id = item.optString("id")
                    val attrs = item.optJSONObject("attributes") ?: continue
                    if (id.isBlank() || output.containsKey(id)) continue
                    val title = attrs.optString("title").ifBlank { "آگهی شیپور" }
                    val location = attrs.optString("location")
                    val prices = attrs.optJSONArray("price")
                    val price = if (prices != null && prices.length() > 0) {
                        prices.optJSONObject(0)?.optString("amount").orEmpty()
                    } else ""
                    val images = attrs.optJSONArray("images")
                    var image = ""
                    if (images != null && images.length() > 0) {
                        val source = images.optJSONObject(0)?.optJSONObject("source")
                        image = source?.optString("mobile").orEmpty().ifBlank { source?.optString("thumbnail").orEmpty() }
                    }
                    val summary = listOf(location, attrs.optString("timePassedLabel")).filter { it.isNotBlank() }.joinToString(" · ")
                    output[id] = DivarOnlineAd(title, summary, price, image, "https://www.sheypoor.com/v/$id", "شیپور")
                    if (output.size >= 30) break
                }
                if (output.size >= 30) break
            }
            return output.values.toList()
        } finally {
            connection.disconnect()
        }
    }
}
