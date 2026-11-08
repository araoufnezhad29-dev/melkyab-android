package ir.melkyab.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import java.text.DecimalFormat

data class Property(
    val id: Long, val kind: String, val title: String, val city: String,
    val area: String, val price: String, val deposit: String, val rent: String,
    val notes: String, val photos: String, val deal: String = "فروش",
    val unitCount: String = "", val floor: String = "", val parking: String = "",
    val elevator: String = "", val storage: String = "", val balcony: String = "",
    val cooling: String = "", val heating: String = "", val direction: String = "",
    val facade: String = "", val master: String = "", val toilet: String = "",
    val cabinet: String = "", val flooring: String = "", val createdAt: Long = 0L
)

class PropertyDb(context: Context) : android.database.sqlite.SQLiteOpenHelper(context, "melkyab.db", null, 4) {
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("""CREATE TABLE properties(
            id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, title TEXT, city TEXT,
            area TEXT, price TEXT, deposit TEXT, rent TEXT, notes TEXT, photos TEXT,
            deal TEXT DEFAULT 'فروش', unitCount TEXT, floor TEXT, parking TEXT, elevator TEXT, storage TEXT, balcony TEXT, cooling TEXT, heating TEXT, direction TEXT, facade TEXT, master TEXT, toilet TEXT, cabinet TEXT, flooring TEXT, createdAt INTEGER NOT NULL DEFAULT 0)""")
    }
    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE properties ADD COLUMN deal TEXT DEFAULT 'فروش'")
            db.execSQL("UPDATE properties SET deal=CASE WHEN deposit IS NOT NULL AND deposit!='' THEN CASE WHEN rent IS NOT NULL AND rent!='' THEN 'رهن و اجاره' ELSE 'رهن کامل' END ELSE 'فروش' END")
        }
        if (oldVersion < 3) {
            listOf("unitCount","floor","parking","elevator","storage","balcony","cooling","heating","direction","facade","master","toilet","cabinet","flooring").forEach {
                db.execSQL("ALTER TABLE properties ADD COLUMN $it TEXT DEFAULT ''")
            }
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE properties ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        }
    }
    fun all(query: String = ""): List<Property> {
        val result = mutableListOf<Property>()
        val c = readableDatabase.rawQuery(
            "SELECT * FROM properties WHERE title LIKE ? OR notes LIKE ? ORDER BY id DESC",
            arrayOf("%" + query + "%", "%" + query + "%")
        )
        c.use {
            while (it.moveToNext()) result.add(Property(
                it.getLong(0), it.getString(1) ?: "", it.getString(2) ?: "",
                it.getString(3) ?: "", it.getString(4) ?: "", it.getString(5) ?: "",
                it.getString(6) ?: "", it.getString(7) ?: "", it.getString(8) ?: "",
                it.getString(9) ?: "[]", it.getString(10) ?: "فروش",
                it.getString(11) ?: "", it.getString(12) ?: "", it.getString(13) ?: "",
                it.getString(14) ?: "", it.getString(15) ?: "", it.getString(16) ?: "",
                it.getString(17) ?: "", it.getString(18) ?: "", it.getString(19) ?: "",
                it.getString(20) ?: "", it.getString(21) ?: "", it.getString(22) ?: "",
                it.getString(23) ?: "", it.getString(24) ?: "", it.getLong(25)
            ))
        }
        return result
    }
    fun save(p: Property) {
        val v = ContentValues().apply {
            put("kind",p.kind); put("title",p.title); put("city",p.city); put("area",p.area)
            put("price",p.price); put("deposit",p.deposit); put("rent",p.rent)
            put("notes",p.notes); put("photos",p.photos); put("deal",p.deal)
            put("unitCount",p.unitCount); put("floor",p.floor); put("parking",p.parking)
            put("elevator",p.elevator); put("storage",p.storage); put("balcony",p.balcony)
            put("cooling",p.cooling); put("heating",p.heating); put("direction",p.direction)
            put("facade",p.facade); put("master",p.master); put("toilet",p.toilet)
            put("cabinet",p.cabinet); put("flooring",p.flooring); put("createdAt",p.createdAt)
        }
        if (p.id == 0L) writableDatabase.insert("properties", null, v)
        else writableDatabase.update("properties", v, "id=?", arrayOf(p.id.toString()))
    }
    fun delete(id: Long) { writableDatabase.delete("properties","id=?",arrayOf(id.toString())) }
}

class MainActivity : Activity() {
    private lateinit var db: PropertyDb
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var selectedPhotos = mutableListOf<String>()
    private var displayMode = "list"
    private var dealFilter = "همه"
    private var activeSection: String? = null
    private val navy = Color.rgb(28, 48, 75)
    private val blue = Color.rgb(48, 105, 170)
    private val pale = Color.rgb(239, 244, 250)
    private val muted = Color.rgb(105, 119, 137)

    private fun choosePhotos() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 101)
    }

    @Deprecated("Legacy activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == Activity.RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) addPhoto(clip.getItemAt(i).uri)
            } else data.data?.let { addPhoto(it) }
            Toast.makeText(this, "تعداد عکس‌ها: " + selectedPhotos.size, Toast.LENGTH_SHORT).show()
        }
    }

    private fun addPhoto(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        if (!selectedPhotos.contains(uri.toString())) selectedPhotos.add(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = PropertyDb(this)
        displayMode = getPreferences(Context.MODE_PRIVATE).getString("display_mode", "list") ?: "list"
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 16, stroke: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke) }

    private fun text(value: String, size: Float = 15f, color: Int = navy, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(dp(8), dp(7), dp(8), dp(7))
    }

    private fun field(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint; setText(value); textSize = 15f
        setSingleLine(hint != "توضیحات")
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(Color.WHITE, 12, Color.rgb(220, 227, 236))
    }

    private fun button(label: String, action: () -> Unit, primary: Boolean = false) = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false
        setTextColor(if (primary) Color.WHITE else navy)
        background = rounded(if (primary) blue else Color.WHITE, 12, if (primary) blue else Color.rgb(220,227,236))
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { action() }
    }

    private fun formatMoney(raw: String): String {
        val ascii = raw.mapNotNull {
            when (it) {
                in '0'..'9' -> it
                in '۰'..'۹' -> ('0'.code + (it.code - '۰'.code)).toChar()
                in '٠'..'٩' -> ('0'.code + (it.code - '٠'.code)).toChar()
                else -> null
            }
        }.joinToString("").trimStart('0')
        if (ascii.isEmpty()) return ""
        val grouped = ascii.reversed().chunked(3).joinToString(".").reversed()
        return grouped.map { if (it in '0'..'9') ('۰'.code + (it.code - '0'.code)).toChar() else it }.joinToString("")
    }

    private fun rawMoney(value: String): String = value.mapNotNull {
        when (it) {
            in '0'..'9' -> it
            in '۰'..'۹' -> ('0'.code + (it.code - '۰'.code)).toChar()
            in '٠'..'٩' -> ('0'.code + (it.code - '٠'.code)).toChar()
            else -> null
        }
    }.joinToString("")

    private fun moneyField(hint: String, value: String): EditText {
        val e = field(hint, formatMoney(value))
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.addTextChangedListener(object : TextWatcher {
            private var changing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (changing) return
                val formatted = formatMoney(s?.toString() ?: "")
                if (formatted != s.toString()) {
                    changing = true
                    e.setText(formatted)
                    e.setSelection(formatted.length)
                    changing = false
                }
            }
        })
        return e
    }

    private fun persianDate(timestamp: Long): String {
        if (timestamp <= 0L) return "نامشخص"
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val gy = cal.get(java.util.Calendar.YEAR)
        val gm = cal.get(java.util.Calendar.MONTH) + 1
        val gd = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val monthDays = intArrayOf(0,31, if (gy % 4 == 0 && (gy % 100 != 0 || gy % 400 == 0)) 29 else 28,31,30,31,30,31,31,30,31,30,31)
        var days = 355666 + 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400 + gd
        for (i in 1 until gm) days += monthDays[i]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return String.format(java.util.Locale.US, "%04d/%02d/%02d", jy, jm, jd)
            .map { if (it in '0'..'9') ('۰'.code + it.code - '0'.code).toChar() else it }.joinToString("")
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16),dp(12),dp(16),dp(8))
            setBackgroundColor(pale)
        }
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16),dp(18),dp(16),dp(18))
            background = rounded(navy, 20)
        }
        header.addView(text("ملک‌یاب", 28f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        header.addView(text("سامان‌دهی هوشمند فایل‌های ملکی", 12f, Color.rgb(215,224,236)).apply { gravity = Gravity.CENTER })
        page.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        search = field("عنوان")
        page.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(12) })
        search.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (activeSection != null) refreshList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val addCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18),dp(15),dp(18),dp(15))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(navy, Color.rgb(43,65,94))
            ).apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.rgb(190,155,85)) }
            isClickable = true
            isFocusable = true
            setOnClickListener { showEditor(null) }
        }
        addCard.addView(text("+", 34f, Color.rgb(224,187,112), true))
        val addText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText.addView(text("ثبت ملک جدید", 18f, Color.WHITE, true))
        addText.addView(text("افزودن فایل و مشخصات ملک", 11f, Color.rgb(220,230,242)))
        addCard.addView(addText, LinearLayout.LayoutParams(0,-2,1f))
        page.addView(addCard, LinearLayout.LayoutParams(-1, dp(82)).apply { bottomMargin = dp(14) })

        page.addView(text("دسته‌بندی آگهی‌ها", 15f, navy, true))
        val categories = listOf("فروش", "رهن", "رهن و اجاره", "کلنگی و مستغلات")
        categories.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { category ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(8),dp(10),dp(8),dp(10))
                    background = rounded(Color.WHITE, 16, Color.rgb(222,228,237))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { activeSection = category; dealFilter = "همه"; renderSection() }
                }
                val symbol = when(category) {
                    "فروش" -> "◇"
                    "رهن" -> "⌂"
                    "رهن و اجاره" -> "▤"
                    else -> "⌑"
                }
                tile.addView(text(symbol, 29f, Color.rgb(176,137,65), true).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(2), 0, dp(4))
                })
                tile.addView(text(category, 14f, navy, true).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    maxLines = 2
                    minLines = 1
                    includeFontPadding = false
                    setPadding(dp(2), dp(8), dp(2), dp(4))
                })
                row.addView(tile, LinearLayout.LayoutParams(0,dp(124),1f).apply {
                    marginEnd = dp(5); marginStart = dp(5); bottomMargin = dp(10)
                })
            }
            page.addView(row)
        }

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        list.removeAllViews()
    }

    private fun renderSection() {
        val section = activeSection ?: return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setBackgroundColor(pale)
        }
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        toolbar.addView(button("‹ بازگشت", {
            activeSection = null
            render()
        }), LinearLayout.LayoutParams(-2, dp(44)))
        toolbar.addView(text(section, 19f, navy, true), LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(8)
        })
        val modeButton = button(if (displayMode == "list") "نمایش: فهرستی ▾" else "نمایش: شبکه‌ای ▾", {
            val popup = PopupMenu(this, toolbar.getChildAt(toolbar.childCount - 1))
            popup.menu.add("نمایش فهرستی").setOnMenuItemClickListener {
                displayMode = "list"
                getPreferences(Context.MODE_PRIVATE).edit().putString("display_mode", displayMode).apply()
                renderSection()
                true
            }
            popup.menu.add("نمایش شبکه‌ای ۴ ستونه").setOnMenuItemClickListener {
                displayMode = "grid"
                getPreferences(Context.MODE_PRIVATE).edit().putString("display_mode", displayMode).apply()
                renderSection()
                true
            }
            popup.show()
        })
        toolbar.addView(modeButton, LinearLayout.LayoutParams(-2, dp(44)))
        page.addView(toolbar)

        search = field("عنوان")
        page.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(12) })
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(results)
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        list = results
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshList() }
            override fun afterTextChanged(s: Editable?) {}
        })
        refreshList()
    }

    private fun showGallery(p: Property) {
        val photos = try { val a = JSONArray(p.photos); MutableList(a.length()) { i -> a.getString(i) } } catch (_: Exception) { mutableListOf<String>() }
        if (photos.isEmpty()) { Toast.makeText(this,"برای این ملک عکسی ثبت نشده است",Toast.LENGTH_SHORT).show(); return }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)) }
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        photos.forEachIndexed { index, path ->
            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                try { setImageURI(Uri.parse(path)) } catch (_: Exception) {}
                setOnClickListener { showLargePhoto(path, index + 1, photos.size) }
            }
            column.addView(text("تصویر " + (index + 1), 12f, muted))
            column.addView(image, LinearLayout.LayoutParams(-1, dp(210)).apply { bottomMargin = dp(12) })
        }
        scroll.addView(column)
        container.addView(scroll)
        AlertDialog.Builder(this).setTitle("تصاویر ملک - " + photos.size + " عکس").setView(container)
            .setPositiveButton("بستن",null).show()
    }

    private fun showLargePhoto(path: String, number: Int, total: Int) {
        val image = ImageView(this).apply {
            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
            try { setImageURI(Uri.parse(path)) } catch (_: Exception) {}
            setBackgroundColor(Color.BLACK)
        }
        AlertDialog.Builder(this).setTitle("تصویر " + number + " از " + total)
            .setView(image).setPositiveButton("بستن",null).show()
    }

    private fun refreshList() {
        if (!::list.isInitialized || activeSection == null) return
        list.removeAllViews()
        val query = if (::search.isInitialized) search.text.toString() else ""
        val found = db.all(query)
        val section = activeSection!!
        val sectionItems = when(section) {
            "فروش" -> found.filter { it.deal == "فروش" }
            "رهن" -> found.filter { it.deal == "رهن کامل" }
            "رهن و اجاره" -> found.filter { it.deal == "رهن و اجاره" }
            "کلنگی و مستغلات" -> found.filter { it.kind == "کلنگی و مستغلات" || it.kind == "زمین و کلنگی" }
            else -> emptyList()
        }
        val items = if (dealFilter == "همه") sectionItems else sectionItems.filter { it.deal == dealFilter }
        if (items.isEmpty()) {
            list.addView(text("در بخش «" + section + "» هنوز فایلی ثبت نشده است.", 14f, muted))
            return
        }
        if (displayMode == "grid") {
            val grid = GridLayout(this).apply { columnCount = 4; alignmentMode = GridLayout.ALIGN_BOUNDS }
            items.forEach { p ->
                val card = makeCard(p, true)
                val lp = GridLayout.LayoutParams().apply {
                    width = 0; height = dp(190); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(2),dp(3),dp(2),dp(3))
                }
                grid.addView(card, lp)
            }
            list.addView(grid)
        } else {
            items.forEach { p ->
                val card = makeCard(p, false)
                list.addView(card, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(9) })
            }
        }
    }

    private fun makeCard(p: Property, compact: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (compact) 4 else 12),dp(if (compact) 5 else 10),dp(if (compact) 4 else 12),dp(if (compact) 5 else 10))
            background = rounded(Color.WHITE, 14, Color.rgb(229,234,241))
        }
        val photoCount = try { JSONArray(p.photos).length() } catch (_: Exception) { 0 }
        if (photoCount > 0) {
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                try { setImageURI(Uri.parse(JSONArray(p.photos).getString(0))) } catch (_: Exception) {}
                background = rounded(pale, 10)
                setOnClickListener { showGallery(p) }
            }
            card.addView(thumb, LinearLayout.LayoutParams(-1, dp(if (compact) 58 else 115)))
        } else if (compact) {
            card.addView(text("بدون عکس", 10f, muted))
        }
        card.addView(text(p.title.ifBlank { "بدون عنوان" }, if (compact) 11f else 17f, navy, true))
        card.addView(text(p.deal, if (compact) 10f else 12f, blue, true))
        card.addView(text(p.kind, if (compact) 9f else 13f, muted))
        card.addView(text(p.area + " متر", if (compact) 9f else 12f, muted))
        val money = when (p.deal) {
            "فروش" -> "قیمت: " + formatMoney(p.price)
            "رهن کامل" -> "رهن: " + formatMoney(p.deposit)
            else -> "رهن: " + formatMoney(p.deposit) + "\nاجاره: " + formatMoney(p.rent)
        }
        card.addView(text(money, if (compact) 9f else 13f, navy, true))
        card.addView(text("تاریخ ثبت: " + persianDate(p.createdAt), if (compact) 8f else 10f, muted).apply { gravity = Gravity.LEFT })
        if (photoCount > 0) card.addView(button("عکس‌ها " + photoCount, { showGallery(p) }))
        val actions = LinearLayout(this).apply { orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        actions.addView(button("ویرایش", { showEditor(p) }), LinearLayout.LayoutParams(if (compact) -1 else 0, dp(38), if (compact) 0f else 1f))
        actions.addView(button("حذف", {
            AlertDialog.Builder(this).setMessage("این ملک حذف شود؟")
                .setNegativeButton("انصراف",null).setPositiveButton("حذف") { _,_ -> db.delete(p.id); refreshList() }.show()
        }), LinearLayout.LayoutParams(if (compact) -1 else 0, dp(38), if (compact) 0f else 1f))
        card.addView(actions)
        return card
    }

    private fun showEditor(existing: Property?) {
        selectedPhotos = try {
            if (existing == null) mutableListOf() else {
                val a=JSONArray(existing.photos); MutableList(a.length()){i->a.getString(i)}
            }
        } catch (_: Exception) { mutableListOf() }

        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(text("۱. نوع معامله را انتخاب کنید", 14f, navy, true))
        val deals = listOf("فروش","رهن کامل","رهن و اجاره")
        val dealSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, deals)
            setSelection(deals.indexOf(existing?.deal).takeIf { it >= 0 } ?: 0)
        }
        content.addView(dealSpinner)
        content.addView(text("۲. مشخصات ملک", 14f, navy, true))
        val kinds = listOf("آپارتمان","خانه و ویلایی","کلنگی و مستغلات","مغازه و تجاری")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, kinds)
            setSelection(kinds.indexOf(existing?.kind).takeIf { it >= 0 } ?: 0)
        }
        content.addView(text("نوع ملک")); content.addView(spinner)
        val title = field("عنوان ملک",existing?.title ?: "")
        val city = field("شهر و محله",existing?.city ?: "")
        val area = field("متراژ (متر)",existing?.area ?: "")
        val price = moneyField("قیمت کل (تومان)",existing?.price ?: "")
        val deposit = moneyField("مبلغ رهن (تومان)",existing?.deposit ?: "")
        val rent = moneyField("اجاره ماهانه (تومان)",existing?.rent ?: "")
        val notes = field("توضیحات",existing?.notes ?: "").apply { minLines = 3; gravity = Gravity.TOP }

        fun option(label: String, values: List<String>, current: String): Spinner {
            content.addView(text(label, 12f, muted, true))
            return Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values)
                setSelection(values.indexOf(current).takeIf { it >= 0 } ?: 0)
            }.also { content.addView(it) }
        }
        content.addView(title); content.addView(area)
        val priceLabel = text("قیمت فروش", 12f, muted, true)
        val depositLabel = text("مبلغ رهن", 12f, muted, true)
        val rentLabel = text("اجاره ماهانه", 12f, muted, true)
        content.addView(priceLabel); content.addView(price)
        content.addView(depositLabel); content.addView(deposit)
        content.addView(rentLabel); content.addView(rent)

        content.addView(text("۳. جزئیات ساختمان و امکانات", 14f, navy, true))
        val unitCount = field("تعداد کل واحدهای ساختمان", existing?.unitCount ?: "").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val floor = field("طبقه چندم", existing?.floor ?: "").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        content.addView(unitCount); content.addView(floor)
        val yesNo = listOf("نامشخص", "دارد", "ندارد")
        val parking = option("پارکینگ", yesNo, existing?.parking ?: "نامشخص")
        val elevator = option("آسانسور", yesNo, existing?.elevator ?: "نامشخص")
        val storage = option("انباری", yesNo, existing?.storage ?: "نامشخص")
        val balcony = option("بالکن", yesNo, existing?.balcony ?: "نامشخص")
        val cooling = option("سیستم سرمایش", listOf("نامشخص","کولر آبی","کولر گازی","داکت اسپلیت","چیلر","فن‌کویل","بدون سیستم"), existing?.cooling ?: "نامشخص")
        val heating = option("سیستم گرمایش", listOf("نامشخص","پکیج و رادیاتور","موتورخانه و رادیاتور","بخاری","گرمایش از کف","داکت اسپلیت","فن‌کویل","بدون سیستم"), existing?.heating ?: "نامشخص")
        val direction = option("جهت ملک", listOf("نامشخص","شمالی","جنوبی","شرقی","غربی","دوکله"), existing?.direction ?: "نامشخص")
        val facade = option("نمای ساختمان", listOf("نامشخص","سنگ","آجر","کامپوزیت","سیمان","ترکیبی","شیشه","سایر"), existing?.facade ?: "نامشخص")
        val master = option("حمام مستر", yesNo, existing?.master ?: "نامشخص")
        val toilet = option("نوع سرویس بهداشتی", listOf("نامشخص","ایرانی","فرنگی","هر دو"), existing?.toilet ?: "نامشخص")
        val cabinet = option("جنس کابینت", listOf("نامشخص","MDF","هایگلاس","ممبران","چوب","فلزی","PVC","سایر"), existing?.cabinet ?: "نامشخص")
        val flooring = option("جنس کف واحد", listOf("نامشخص","سرامیک","سنگ","پارکت","لمینت","موزاییک","موکت","ترکیبی","سایر"), existing?.flooring ?: "نامشخص")

        fun updateDealFields() {
            when (dealSpinner.selectedItem.toString()) {
                "فروش" -> { priceLabel.visibility=View.VISIBLE; price.visibility=View.VISIBLE; depositLabel.visibility=View.GONE; deposit.visibility=View.GONE; rentLabel.visibility=View.GONE; rent.visibility=View.GONE }
                "رهن کامل" -> { priceLabel.visibility=View.GONE; price.visibility=View.GONE; depositLabel.visibility=View.VISIBLE; deposit.visibility=View.VISIBLE; rentLabel.visibility=View.GONE; rent.visibility=View.GONE }
                else -> { priceLabel.visibility=View.GONE; price.visibility=View.GONE; depositLabel.visibility=View.VISIBLE; deposit.visibility=View.VISIBLE; rentLabel.visibility=View.VISIBLE; rent.visibility=View.VISIBLE }
            }
        }
        dealSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { updateDealFields() }
        }
        updateDealFields()
        content.addView(text("تصاویر ملک", 14f, navy, true))
        val photoStatus = text("عکس‌های انتخاب‌شده: " + selectedPhotos.size, 12f, muted)
        content.addView(photoStatus)
        content.addView(button("انتخاب عکس از گوشی", { choosePhotos(); photoStatus.text = "برای به‌روزرسانی تعداد، دوباره فرم را باز کنید." }))
        content.addView(button("مشاهده عکس‌های انتخاب‌شده", { showSelectedGallery() }))
        content.addView(text("توضیحات", 12f, muted, true)); content.addView(notes)
        scroll.addView(content); wrap.addView(scroll)
        AlertDialog.Builder(this).setTitle(if(existing==null) "ثبت ملک جدید" else "ویرایش ملک")
            .setView(wrap)
            .setNegativeButton("انصراف",null)
            .setPositiveButton("ذخیره") { _,_ ->
                if (title.text.toString().isBlank()) {
                    Toast.makeText(this,"عنوان ملک را وارد کن",Toast.LENGTH_SHORT).show()
                } else {
                    val deal = dealSpinner.selectedItem.toString()
                    val photos = JSONArray().apply { selectedPhotos.forEach { put(it) } }.toString()
                    db.save(Property(existing?.id ?: 0L,spinner.selectedItem.toString(),
                        title.text.toString(),"",area.text.toString(),
                        if (deal == "فروش") rawMoney(price.text.toString()) else "",
                        if (deal != "فروش") rawMoney(deposit.text.toString()) else "",
                        if (deal == "رهن و اجاره") rawMoney(rent.text.toString()) else "",
                        notes.text.toString(),photos,deal,
                        unitCount.text.toString(),floor.text.toString(),
                        parking.selectedItem.toString(),elevator.selectedItem.toString(),
                        storage.selectedItem.toString(),balcony.selectedItem.toString(),
                        cooling.selectedItem.toString(),heating.selectedItem.toString(),
                        direction.selectedItem.toString(),facade.selectedItem.toString(),
                        master.selectedItem.toString(),toilet.selectedItem.toString(),
                        cabinet.selectedItem.toString(),flooring.selectedItem.toString(),
                        existing?.createdAt ?: System.currentTimeMillis()))
                    refreshList()
                }
            }.show()
    }

    private fun showSelectedGallery() {
        if (selectedPhotos.isEmpty()) { Toast.makeText(this,"هنوز عکسی انتخاب نشده است",Toast.LENGTH_SHORT).show(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)) }
        val sc = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        selectedPhotos.toList().forEachIndexed { index, path ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageURI(Uri.parse(path)) }
            row.addView(img, LinearLayout.LayoutParams(dp(90),dp(75)))
            row.addView(text("تصویر " + (index+1),13f), LinearLayout.LayoutParams(0,-2,1f))
            row.addView(button("حذف", { selectedPhotos.remove(path); Toast.makeText(this,"عکس از فهرست انتخاب‌ها حذف شد؛ فرم را دوباره باز کن.",Toast.LENGTH_SHORT).show() }))
            col.addView(row)
        }
        sc.addView(col); box.addView(sc)
        AlertDialog.Builder(this).setTitle("عکس‌های انتخاب‌شده").setView(box).setPositiveButton("بستن",null).show()
    }

    override fun onDestroy() { db.close(); super.onDestroy() }
}
