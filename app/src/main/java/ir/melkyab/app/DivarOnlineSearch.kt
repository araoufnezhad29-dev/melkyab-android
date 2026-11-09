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
    @Volatile private var tehranDistrictCache: List<Pair<String, String>> = emptyList()

    fun getTehranDistricts(): List<Pair<String, String>> {
        if (tehranDistrictCache.isNotEmpty()) return tehranDistrictCache
        val connection = (URL("https://api.divar.ir/v8/places/cities/1/districts").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 18000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Origin", "https://divar.ir")
            setRequestProperty("Referer", "https://divar.ir/")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val root = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val array = root.optJSONArray("districts") ?: root.optJSONArray("data") ?: return emptyList()
            val items = ArrayList<Pair<String, String>>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val name = item.optString("name").trim()
                if (id.isNotBlank() && name.isNotBlank()) items.add(name to id)
            }
            tehranDistrictCache = items.distinctBy { it.second }
            return tehranDistrictCache
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeDistrict(value: String): String = value.trim()
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace("‌", "").replace(" ", "").replace("-", "")

    fun districtId(name: String): String? {
        if (name.isBlank()) return null
        val items = getTehranDistricts()
        val normalized = normalizeDistrict(name)
        return items.firstOrNull { normalizeDistrict(it.first) == normalized }?.second
            ?: items.firstOrNull { normalizeDistrict(it.first).contains(normalized) || normalized.contains(normalizeDistrict(it.first)) }?.second
    }

    fun search(categoryPath: String, query: String, filters: JSONObject = JSONObject()): List<DivarOnlineAd> {
        val category = when (categoryPath) {
            "buy-villa" -> "villa-sell"
            "rent-villa" -> "villa-rent"
            "buy-old-house" -> "old-house-sell"
            "rent-old-house" -> "old-house-rent"
            "buy-commercial" -> "buy-commercial-property"
            "rent-commercial" -> "rent-commercial-property"
            "rent-apartment" -> "apartment-rent"
            else -> "apartment-sell"
        }

        // Divar's web search endpoint expects typed form filters and a compact search payload.
        // Extra UI/map fields can cause the endpoint to reject otherwise valid requests.
        val formData = JSONObject().put("category", JSONObject().put("str", JSONObject().put("value", category)))
        val filterKeys = filters.keys()
        while (filterKeys.hasNext()) {
            val key = filterKeys.next()
            val value = filters.opt(key)
            if (value != null && value != JSONObject.NULL) formData.put(key, value)
        }

        // Divar's first-page endpoint requires pagination_data. Keep the
        // payload minimal and use the same shape verified against the public API.
        formData.put("sort", JSONObject().put("str", JSONObject().put("value", "sort_date")))
        val body = JSONObject()
            .put("city_ids", org.json.JSONArray().put("1"))
            .put("search_data", JSONObject()
                .put("form_data", JSONObject().put("data", formData)))
            .put("pagination_data", JSONObject()
                .put("@type", "type.googleapis.com/post_list.PaginationData")
                .put("page", 1))

        val connection = (URL("https://api.divar.ir/v8/postlist/w/search").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 18000
            readTimeout = 25000
            doOutput = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Origin", "https://divar.ir")
            setRequestProperty("Referer", "https://divar.ir/")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val errorText = try {
                    (connection.errorStream ?: connection.inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } catch (_: Exception) { "" }
                throw IllegalStateException("دیوار پاسخ HTTP $status داد" + if (errorText.isBlank()) "" else ": " + errorText.take(180))
            }

            val json = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            // Divar has returned more than one public response envelope over time.
            // Accept both the classic list_widgets array and the web_widgets.post_list form.
            val webWidgets = json.optJSONObject("webWidgets") ?: json.optJSONObject("web_widgets")
            val widgets = json.optJSONArray("list_widgets")
                ?: json.optJSONArray("post_list")
                ?: webWidgets?.optJSONArray("post_list")
                ?: webWidgets?.optJSONArray("list_widgets")
                ?: throw IllegalStateException(
                    "ساختار پاسخ دیوار شناخته‌شده نیست؛ کلیدها: " +
                        (0 until json.length()).joinToString(",") { json.names()?.optString(it).orEmpty() } +
                        "؛ پاسخ: " + json.toString().take(260)
                )
            val result = LinkedHashMap<String, DivarOnlineAd>()
            val widgetTypes = ArrayList<String>()
            for (i in 0 until widgets.length()) {
                val widget = widgets.optJSONObject(i) ?: continue
                val widgetType = widget.optString("widget_type")
                if (widgetType.isNotBlank()) widgetTypes.add(widgetType)
                if (widgetType != "POST_ROW") continue
                val data = widget.optJSONObject("data") ?: widget.optJSONObject("post_row") ?: continue
                val action = data.optJSONObject("action")?.optJSONObject("payload")
                val token = action?.optString("token").orEmpty().ifBlank { data.optString("token") }
                if (token.isBlank() || result.containsKey(token)) continue
                val title = data.optString("title").ifBlank { "آگهی دیوار" }
                val webInfo = action?.optJSONObject("web_info")
                val district = webInfo?.optString("district_persian").orEmpty()
                val bottom = data.optString("bottom_description_text")
                val summary = listOf(district, bottom).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                val price = listOf(data.optString("top_description_text"), data.optString("middle_description_text"))
                    .filter { it.isNotBlank() }.distinct().joinToString(" · ")
                val image = data.optString("image_url")
                result[token] = DivarOnlineAd(title, summary, price, image, "https://divar.ir/v/$token", "دیوار")
                if (result.size >= 30) break
            }
            if (widgets.length() > 0 && result.isEmpty()) {
                throw IllegalStateException(
                    "دیوار " + widgets.length() + " ویجت فرستاد، اما POST_ROW پیدا نشد؛ انواع: " +
                        widgetTypes.distinct().take(8).joinToString(",")
                )
            }
            return result.values.toList()
        } finally {
            connection.disconnect()
        }
    }
}
