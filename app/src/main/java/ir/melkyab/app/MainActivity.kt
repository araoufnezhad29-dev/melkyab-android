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
import android.os.Build
import android.window.OnBackInvokedCallback
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
    val cabinet: String = "", val flooring: String = "", val createdAt: Long = 0L,
    val address: String = ""
)

class PropertyDb(context: Context) : android.database.sqlite.SQLiteOpenHelper(context, "melkyab.db", null, 5) {
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("""CREATE TABLE properties(
            id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, title TEXT, city TEXT,
            area TEXT, price TEXT, deposit TEXT, rent TEXT, notes TEXT, photos TEXT,
            deal TEXT DEFAULT 'فروش', unitCount TEXT, floor TEXT, parking TEXT, elevator TEXT, storage TEXT, balcony TEXT, cooling TEXT, heating TEXT, direction TEXT, facade TEXT, master TEXT, toilet TEXT, cabinet TEXT, flooring TEXT, createdAt INTEGER NOT NULL DEFAULT 0, address TEXT DEFAULT '')""")
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
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE properties ADD COLUMN address TEXT DEFAULT ''")
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
                it.getString(23) ?: "", it.getString(24) ?: "", it.getLong(25), it.getString(26) ?: ""
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
            put("cabinet",p.cabinet); put("flooring",p.flooring); put("createdAt",p.createdAt); put("address",p.address)
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
    private var systemBackCallback: OnBackInvokedCallback? = null
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
        if (Build.VERSION.SDK_INT >= 33) {
            systemBackCallback = OnBackInvokedCallback { handleSystemBack() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                systemBackCallback!!
            )
        }
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
            setPadding(dp(8),dp(3),dp(8),dp(3))
            setBackgroundColor(pale)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brandMark = ImageView(this).apply {
            setImageResource(R.drawable.ic_melkyab_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "لوگوی ملک‌یاب؛ خانه و ذره‌بین طلایی"
        }
        val logoWrap = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(5,25,59))
            addView(brandMark, FrameLayout.LayoutParams(dp(86),dp(86),Gravity.CENTER))
        }
        page.addView(logoWrap, LinearLayout.LayoutParams(-1, dp(86)).apply { bottomMargin = dp(1) })

        val slogan = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(1), dp(2), dp(1))
            setBackgroundColor(Color.TRANSPARENT)
        }
        slogan.addView(text("ساماندهی هوشمند فایل‌های ملکی", 13f, navy, true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        })
        slogan.addView(text("ثبت، دسته‌بندی و مدیریت آسان آگهی‌ها", 8f, muted).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        })
        page.addView(slogan, LinearLayout.LayoutParams(-1, dp(34)).apply { bottomMargin = dp(2) })

        search = field("عنوان")
        page.addView(search, LinearLayout.LayoutParams(-1, dp(36)).apply { bottomMargin = dp(1) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        search.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshList() }
            override fun afterTextChanged(s: Editable?) {}
        })

        val addCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8),dp(3),dp(8),dp(3))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(12,30,57), Color.rgb(37,62,91), Color.rgb(12,30,57))
            ).apply { cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.rgb(205,166,91)) }
            elevation = dp(4).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { showEditor(null) }
        }
        val plus = TextView(this).apply {
            text = "+"
            textSize = 26f
            setTextColor(Color.rgb(235,195,112))
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(53,72,96), 14, Color.rgb(190,155,85))
        }
        addCard.addView(plus, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) })
        val addText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        addText.addView(text("ثبت ملک جدید", 13f, Color.WHITE, true))
        addText.addView(text("افزودن فایل و مشخصات ملک", 8f, Color.rgb(220,230,242)))
        addCard.addView(addText, LinearLayout.LayoutParams(0,-2,1f))
        page.addView(addCard, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(2) })

        val categoryTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(1), dp(3), dp(3))
        }
        categoryTitle.addView(text("دسته‌بندی آگهی‌ها", 12f, navy, true), LinearLayout.LayoutParams(0,-2,1f))
        categoryTitle.addView(text("انتخاب بخش موردنظر", 10f, muted))
        page.addView(categoryTitle)
        val categories = listOf("فروش", "رهن", "رهن و اجاره", "کلنگی و مستغلات")
        categories.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { category ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(5),dp(3),dp(5),dp(3))
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.WHITE, Color.rgb(250,248,242))
                    ).apply { cornerRadius = dp(19).toFloat(); setStroke(dp(1), Color.rgb(224,216,198)) }
                    elevation = dp(3).toFloat()
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
                val iconCircle = TextView(this).apply {
                    text = symbol
                    textSize = 17f
                    setTextColor(Color.rgb(174,133,59))
                    gravity = Gravity.CENTER
                    background = rounded(Color.rgb(247,241,226), 30, Color.rgb(230,211,169))
                }
                tile.addView(iconCircle, LinearLayout.LayoutParams(dp(28), dp(28)).apply { bottomMargin = dp(8) })
                tile.addView(text(category, 14f, navy, true).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    maxLines = 2
                    minLines = 1
                    includeFontPadding = false
                    setPadding(dp(2), dp(3), dp(2), dp(3))
                })
                row.addView(tile, LinearLayout.LayoutParams(0,dp(62),1f).apply {
                    marginEnd = dp(5); marginStart = dp(5); bottomMargin = dp(5)
                })
            }
            page.addView(row)
        }

        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        val credit = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = Color.TRANSPARENT
        }
        credit.addView(text("طراح: مهندس رئوف نژاد", 11f, navy, true).apply {
            gravity = Gravity.CENTER
        })
        root.addView(credit, LinearLayout.LayoutParams(-1, dp(24)).apply {
            topMargin = dp(0); bottomMargin = dp(0)
        })
        setContentView(root)
        list.removeAllViews()
    }

    private fun renderSection() {
        val section = activeSection ?: return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(8))
            setBackgroundColor(pale)
        }
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = rounded(Color.WHITE, 15, Color.rgb(224,216,198))
            elevation = dp(2).toFloat()
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
        page.addView(toolbar, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(24)
            bottomMargin = dp(14)
        })

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

    private var currentGalleryPhotos: List<String> = emptyList()

    private fun showPropertyDetails(p: Property) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val sc = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val category = if (p.kind == "کلنگی و مستغلات" || p.kind == "زمین و کلنگی") "کلنگی و مستغلات" else p.deal
        val sqm = rawMoney(p.area).toLongOrNull() ?: 0L
        val pricePerMeter = if (p.deal == "فروش" && sqm > 0) formatMoney(((p.price.toLongOrNull() ?: 0L) / sqm).toString()) else ""
        listOf("دسته‌بندی: " + category, "عنوان: " + p.title, "نوع ملک: " + p.kind,
            "متراژ: " + p.area + " متر", "آدرس: " + p.address,
            "قیمت کل: " + formatMoney(p.price), "قیمت هر متر مربع: " + pricePerMeter,
            "مبلغ رهن: " + formatMoney(p.deposit), "اجاره ماهانه: " + formatMoney(p.rent),
            "تعداد واحدهای ساختمان: " + p.unitCount, "طبقه: " + p.floor,
            "پارکینگ: " + p.parking, "آسانسور: " + p.elevator, "انباری: " + p.storage,
            "بالکن: " + p.balcony, "سیستم سرمایش: " + p.cooling, "سیستم گرمایش: " + p.heating,
            "جهت ملک: " + p.direction, "نمای ساختمان: " + p.facade, "حمام مستر: " + p.master,
            "سرویس بهداشتی: " + p.toilet, "کابینت: " + p.cabinet, "کف‌پوش: " + p.flooring,
            "توضیحات: " + p.notes, "تاریخ ثبت: " + persianDate(p.createdAt))
            .filter { it.substringAfter(": ").isNotBlank() }
            .forEach { content.addView(text(it,14f,navy)) }
        sc.addView(content); box.addView(sc)
        AlertDialog.Builder(this).setTitle(p.title.ifBlank { "مشخصات آگهی" }).setView(box)
            .setNeutralButton("عکس‌ها") { _,_ -> showGallery(p) }
            .setNegativeButton("بستن",null)
            .setPositiveButton("ویرایش") { _,_ -> showEditor(p) }.show()
    }

    private fun showGallery(p: Property) {
        val photos = try { val a = JSONArray(p.photos); MutableList(a.length()) { i -> a.getString(i) } } catch (_: Exception) { mutableListOf<String>() }
        currentGalleryPhotos = photos
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
        var index = number - 1
        val image = ImageView(this).apply {
            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
            try { setImageURI(Uri.parse(path)) } catch (_: Exception) {}
            setBackgroundColor(Color.BLACK)
        }
        val counter = text("تصویر $number از $total",14f,navy,true)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(counter)
        content.addView(image, LinearLayout.LayoutParams(-1,dp(420)))
        var startX = 0f
        image.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { startX = event.x; true }
                android.view.MotionEvent.ACTION_UP -> {
                    val delta = event.x - startX
                    if (kotlin.math.abs(delta) > dp(45) && currentGalleryPhotos.isNotEmpty()) {
                        index = if (delta < 0) (index + 1) % currentGalleryPhotos.size
                            else (index - 1 + currentGalleryPhotos.size) % currentGalleryPhotos.size
                        image.setImageURI(Uri.parse(currentGalleryPhotos[index]))
                        counter.text = "تصویر " + (index + 1) + " از " + currentGalleryPhotos.size
                    }
                    true
                }
                else -> true
            }
        }
        AlertDialog.Builder(this).setView(content).setPositiveButton("بستن",null).show()
    }

    private fun refreshList() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val query = if (::search.isInitialized) search.text.toString() else ""
        val allItems = db.all()
        val normalized = query.map { ch -> when(ch) {
            in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
            in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
            else -> ch
        } }.joinToString("").lowercase().replace("ي","ی").replace("ك","ک")
        val amountPattern = Regex("""(?<!\d)(\d{1,3}(?:[.,٬]\d{3})+|\d+(?:[.,]\d+)?)\s*(میلیارد|میلیون|هزار)?""")
        val amountTokens = amountPattern.findAll(normalized).toList()
        val textWords = normalized.replace(amountPattern, " ")
            .split(Regex("""[\s,،٬]+"""))
            .filter { it.isNotBlank() && it !in listOf("تا","حدود","قیمت","متر","متراژ","تومان","تومن","میلیارد","میلیون","هزار") }
        val found = if (query.isBlank()) allItems else allItems.filter { p ->
            val searchable = listOf(p.title,p.notes,p.kind,p.deal,p.address)
                .joinToString(" ").lowercase().replace("ي","ی").replace("ك","ک")
            val textOk = textWords.all { word ->
                searchable.contains(word) || (word == "پیش" && p.deal != "فروش")
            }
            val numberOk = amountTokens.all { token ->
                val numeric = token.groupValues[1].replace(Regex("[.,٬]"), "").toLongOrNull() ?: 0L
                val multiplier = when(token.groupValues[2]) {
                    "میلیارد" -> 1_000_000_000L
                    "میلیون" -> 1_000_000L
                    "هزار" -> 1_000L
                    else -> 1L
                }
                val budget = if (numeric > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else numeric * multiplier
                val before = normalized.substring(0, token.range.first)
                val candidates = when {
                    before.contains("اجاره") -> listOf(rawMoney(p.rent).toLongOrNull() ?: 0L)
                    before.contains("رهن") || before.contains("پیش") -> listOf(rawMoney(p.deposit).toLongOrNull() ?: 0L)
                    before.contains("فروش") || before.contains("قیمت") -> listOf(rawMoney(p.price).toLongOrNull() ?: 0L)
                    p.deal == "فروش" -> listOf(rawMoney(p.price).toLongOrNull() ?: 0L)
                    p.deal == "رهن کامل" -> listOf(rawMoney(p.deposit).toLongOrNull() ?: 0L)
                    else -> listOf(rawMoney(p.deposit).toLongOrNull() ?: 0L)
                }.filter { it > 0L }
                val isCeiling = multiplier > 1L || before.contains("تا") || numeric.toString().length <= 3
                if (candidates.isEmpty()) false
                else if (isCeiling) candidates.any { it <= budget }
                else candidates.any { it == budget }
            }
            textOk && numberOk
        }
        val section = activeSection
        val sectionItems = if (section == null || query.isNotBlank()) found else when(section) {
            "فروش" -> found.filter { it.deal == "فروش" }
            "رهن" -> found.filter { it.deal == "رهن کامل" }
            "رهن و اجاره" -> found.filter { it.deal == "رهن و اجاره" }
            "کلنگی و مستغلات" -> found.filter { it.kind == "کلنگی و مستغلات" || it.kind == "زمین و کلنگی" }
            else -> emptyList()
        }
        val items = if (section == null || dealFilter == "همه") sectionItems else sectionItems.filter { it.deal == dealFilter }
        if (items.isEmpty()) {
            if (query.isNotBlank()) list.addView(text("نتیجه‌ای برای این جست‌وجو پیدا نشد.", 14f, muted))
            else if (section != null) list.addView(text("در بخش «" + section + "» هنوز فایلی ثبت نشده است.", 14f, muted))
            return
        }
        if (query.isNotBlank()) {
            items.take(6).forEach { p ->
                val category = if (p.kind == "کلنگی و مستغلات" || p.kind == "زمین و کلنگی") "کلنگی و مستغلات" else when(p.deal) {
                    "فروش" -> "فروش"; "رهن کامل" -> "رهن کامل"; else -> "رهن و اجاره"
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(7),dp(4),dp(7),dp(4))
                    background = rounded(Color.WHITE,11,Color.rgb(220,227,236))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showPropertyDetails(p) }
                }
                val photo = try { val a=JSONArray(p.photos); if(a.length()>0) a.getString(0) else "" } catch (_:Exception) { "" }
                if (photo.isNotBlank()) {
                    val thumb = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        try { setImageURI(Uri.parse(photo)) } catch (_:Exception) {}
                    }
                    row.addView(thumb, LinearLayout.LayoutParams(dp(42),dp(42)).apply { marginEnd=dp(7) })
                }
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                info.addView(text(p.title.ifBlank { "بدون عنوان" },11f,navy,true).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END })
                val priceLine = when(p.deal) {
                    "فروش" -> "قیمت: " + formatMoney(p.price) + " تومان"
                    "رهن کامل" -> "رهن: " + formatMoney(p.deposit) + " تومان"
                    else -> "پیش: " + formatMoney(p.deposit) + " · اجاره: " + formatMoney(p.rent)
                }
                info.addView(text(priceLine,9f,blue,true).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END })
                info.addView(text(category + "  |  " + p.area + " متر",8f,muted).apply { maxLines=1 })
                row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(text("›",18f,Color.rgb(174,133,59),true))
                list.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(3) })
            }
            if (items.size > 6) list.addView(text("۶ مورد اول از " + items.size + " آگهی؛ برای مشاهده همه، جست‌وجو را دقیق‌تر کن.",9f,muted))
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
        if (p.address.isNotBlank()) card.addView(text("آدرس: " + p.address, if (compact) 8f else 11f, muted))
        val sqm = rawMoney(p.area).toLongOrNull() ?: 0L
        val salePrice = p.price.toLongOrNull() ?: 0L
        if (p.deal == "فروش" && sqm > 0 && salePrice > 0) {
            card.addView(text("هر متر: " + formatMoney((salePrice / sqm).toString()) + " تومان", if (compact) 8f else 11f, Color.rgb(174,133,59), true))
        }
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
        card.setOnClickListener { showPropertyDetails(p) }
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
        val address = field("آدرس ملک",existing?.address ?: "")
        val area = field("متراژ (متر)",existing?.area ?: "").apply { inputType = InputType.TYPE_CLASS_NUMBER }
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
        content.addView(title); content.addView(address); content.addView(area)
        val pricePerMeter = text("قیمت هر متر: —", 13f, Color.rgb(174,133,59), true).apply {
            background = rounded(Color.rgb(250,247,239), 12, Color.rgb(208,177,112))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        fun updatePricePerMeter() {
            val sqm = rawMoney(area.text.toString()).toLongOrNull() ?: 0L
            val total = rawMoney(price.text.toString()).toLongOrNull() ?: 0L
            pricePerMeter.text = if (sqm > 0 && total > 0) "قیمت هر متر: " + formatMoney((total / sqm).toString()) + " تومان" else "قیمت هر متر: —"
        }
        val meterWatcher = object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePricePerMeter() }
            override fun afterTextChanged(s: Editable?) {}
        }
        area.addTextChangedListener(meterWatcher)
        price.addTextChangedListener(meterWatcher)
        val priceLabel = text("قیمت فروش", 12f, muted, true)
        val depositLabel = text("مبلغ رهن", 12f, muted, true)
        val rentLabel = text("اجاره ماهانه", 12f, muted, true)
        fun attachAmountSuggestions(target: EditText, fieldName: String) {
            val suggestions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
            }
            content.addView(suggestions, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(4) })
            target.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val typed = rawMoney(s?.toString() ?: "").toLongOrNull() ?: 0L
                    suggestions.removeAllViews()
                    if (typed <= 0L) { suggestions.visibility = View.GONE; return }
                    suggestions.visibility = View.VISIBLE
                    suggestions.addView(text("پیشنهاد $fieldName:",9f,muted,true))
                    val scales = listOf(1_000_000L,100_000_000L,1_000_000_000L,10_000_000_000L)
                    scales.forEach { scale ->
                        val amount = if (typed > Long.MAX_VALUE / scale) Long.MAX_VALUE else typed * scale
                        val label = when {
                            amount % 1_000_000_000L == 0L -> formatMoney((amount / 1_000_000_000L).toString()) + " میلیارد"
                            amount % 1_000_000L == 0L -> formatMoney((amount / 1_000_000L).toString()) + " میلیون"
                            else -> formatMoney(amount.toString())
                        }
                        val chip = TextView(this@MainActivity).apply {
                            text = label
                            textSize = 9f
                            setTextColor(blue)
                            gravity = Gravity.CENTER
                            setPadding(dp(5),dp(3),dp(5),dp(3))
                            background = rounded(Color.WHITE,8,Color.rgb(220,227,236))
                            isClickable = true
                            setOnClickListener {
                                target.setText(formatMoney(amount.toString()))
                                target.setSelection(target.text.length)
                            }
                        }
                        suggestions.addView(chip,LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(3) })
                    }
                }
            })
        }
        attachAmountSuggestions(price,"قیمت")
        attachAmountSuggestions(deposit,"رهن")
        attachAmountSuggestions(rent,"اجاره")
        content.addView(priceLabel); content.addView(price); content.addView(pricePerMeter)
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
                        title.text.toString(),"",rawMoney(area.text.toString()),
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
                        existing?.createdAt ?: System.currentTimeMillis(), address.text.toString()))
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

    private fun handleSystemBack() {
        if (activeSection != null) {
            activeSection = null
            dealFilter = "همه"
            render()
        } else {
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleSystemBack()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            systemBackCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
        db.close()
        super.onDestroy()
    }
}
