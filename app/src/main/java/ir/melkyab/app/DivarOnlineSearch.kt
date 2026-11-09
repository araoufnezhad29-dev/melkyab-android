package ir.melkyab.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class DivarOnlineAd(
    val title: String,
    val summary: String,
    val price: String,
    val imageUrl: String,
    val url: String,
    val source: String = "دیوار"
)

object DivarOnlineSearch {
    fun search(categoryPath: String, query: String): List<DivarOnlineAd> {
        val category = when (categoryPath) {
            "buy-villa" -> "villa-sell"
            "rent-villa" -> "villa-rent"
            "buy-commercial" -> "commercial-sell"
            "rent-commercial" -> "commercial-rent"
            "buy-old-house" -> "old-house-sell"
            "rent-old-house" -> "old-house-rent"
            "rent-apartment" -> "apartment-rent"
            else -> "apartment-sell"
        }
        val formData = JSONObject().put("category", JSONObject().put("str", JSONObject().put("value", category)))
        val body = JSONObject()
            .put("source_view", "FILTER")
            .put("city_ids", org.json.JSONArray().put("1"))
            .put("user_selected_location", JSONObject().put("places", org.json.JSONArray().put(JSONObject().put("place_id", "1"))))
            .put("search_data", JSONObject()
                .put("form_data", JSONObject().put("data", formData))
                .put("server_payload", JSONObject().put("additional_form_data",
                    JSONObject().put("data", JSONObject().put("sort", JSONObject().put("str", JSONObject().put("value", "sort_date")))))))
        val connection = (URL("https://api.divar.ir/v8/postlist/w/search").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 18000
            readTimeout = 25000
            doOutput = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Referer", "https://divar.ir/")
            setRequestProperty("X-Web-Serving-Mode", "desktop")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return emptyList()
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            val widgets = json.optJSONArray("list_widgets") ?: return emptyList()
            val result = LinkedHashMap<String, DivarOnlineAd>()
            for (i in 0 until widgets.length()) {
                val widget = widgets.optJSONObject(i) ?: continue
                if (widget.optString("widget_type") != "POST_ROW") continue
                val data = widget.optJSONObject("data") ?: continue
                val action = data.optJSONObject("action")?.optJSONObject("payload")
                val token = action?.optString("token").orEmpty().ifBlank { data.optString("token") }
                if (token.isBlank() || result.containsKey(token)) continue
                val title = data.optString("title").ifBlank { "آگهی دیوار" }
                val summary = data.optString("bottom_description_text")
                val price = data.optString("middle_description_text")
                val image = data.optString("image_url")
                result[token] = DivarOnlineAd(title, summary, price, image, "https://divar.ir/v/$token", "دیوار")
                if (result.size >= 30) break
            }
            return result.values.toList()
        } finally {
            connection.disconnect()
        }
    }
}
