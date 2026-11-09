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
    val address: String = "", val buildingFloors: String = "",
    val singleUnit: String = "بله", val unitsPerFloor: String = "۱"
)

class PropertyDb(context: Context) : android.database.sqlite.SQLiteOpenHelper(context, "melkyab.db", null, 6) {
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("""CREATE TABLE properties(
            id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, title TEXT, city TEXT,
            area TEXT, price TEXT, deposit TEXT, rent TEXT, notes TEXT, photos TEXT,
            deal TEXT DEFAULT 'فروش', unitCount TEXT, floor TEXT, parking TEXT, elevator TEXT, storage TEXT, balcony TEXT, cooling TEXT, heating TEXT, direction TEXT, facade TEXT, master TEXT, toilet TEXT, cabinet TEXT, flooring TEXT, createdAt INTEGER NOT NULL DEFAULT 0, address TEXT DEFAULT '', buildingFloors TEXT DEFAULT '', singleUnit TEXT DEFAULT 'بله', unitsPerFloor TEXT DEFAULT '۱')""")
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
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE properties ADD COLUMN buildingFloors TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE properties ADD COLUMN singleUnit TEXT DEFAULT 'بله'")
            db.execSQL("ALTER TABLE properties ADD COLUMN unitsPerFloor TEXT DEFAULT '۱'")
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
                it.getString(23) ?: "", it.getString(24) ?: "", it.getLong(25), it.getString(26) ?: "",
                it.getString(27) ?: "", it.getString(28) ?: "بله", it.getString(29) ?: "۱"
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
            put("buildingFloors",p.buildingFloors); put("singleUnit",p.singleUnit); put("unitsPerFloor",p.unitsPerFloor)
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
    private var searchSubmitted = false
    private var systemBackCallback: OnBackInvokedCallback? = null
    // ملک‌یاب visual identity: deep midnight navy, warm ivory and refined antique gold.
    private val navy = Color.rgb(28, 43, 57)
    private val blue = Color.rgb(36, 119, 124)
    private val pale = Color.rgb(239, 243, 240)
    private val muted = Color.rgb(105, 119, 121)
    private val gold = Color.rgb(205, 126, 83)
    private val ink = Color.rgb(34, 54, 61)
    private val softGold = Color.rgb(232, 243, 239)

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

    // Prevent Android's "child already has a parent" crash when a dynamic view is reused.
    private fun ViewGroup.addViewSafely(child: View, params: ViewGroup.LayoutParams? = null) {
        (child.parent as? ViewGroup)?.removeView(child)
        if (params == null) addView(child) else addView(child, params)
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
        background = rounded(Color.WHITE, 15, Color.rgb(220, 204, 169))
        setTextColor(ink)
        setHintTextColor(Color.rgb(153, 151, 143))
        elevation = dp(1).toFloat()
    }

    private fun button(label: String, action: () -> Unit, primary: Boolean = false) = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false
        setTextColor(if (primary) Color.WHITE else navy)
        background = if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(178,139,72), Color.rgb(137,99,43))).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(208,177,119))
            } else rounded(Color.WHITE, 14, Color.rgb(220,204,169))
        elevation = dp(if (primary) 3 else 1).toFloat()
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

    private fun logoOnHeader(): android.graphics.Bitmap? {
        val source = BitmapFactory.decodeResource(resources, R.drawable.ic_melkyab_mark) ?: return null
        val bitmap = source.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height
        val visited = BooleanArray(width * height)
        val queue = java.util.ArrayDeque<Int>()
        // Start only from the four outer corners so gold artwork is never removed.
        listOf(0, width - 1, (height - 1) * width, height * width - 1).forEach { queue.add(it) }
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            if (index < 0 || index >= width * height || visited[index]) continue
            visited[index] = true
            val x = index % width
            val y = index / width
            val pixel = bitmap.getPixel(x, y)
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            // Use a global color key: the rounded-square backdrop is a blue gradient
            // and may be separated from the corners by a dark outline.
            for (py in y until minOf(y + 1, height)) {
                for (px in x until minOf(x + 1, width)) {
                    val p = bitmap.getPixel(px, py)
                    val r = Color.red(p)
                    val g = Color.green(p)
                    val b = Color.blue(p)
                    if (b > r + 18 && b > g + 4 && r < 115 && g < 145) {
                        bitmap.setPixel(px, py, Color.TRANSPARENT)
                    }
                }
            }
            if (x > 0) queue.add(index - 1)
            if (x < width - 1) queue.add(index + 1)
            if (y > 0) queue.add(index - width)
            if (y < height - 1) queue.add(index + width)
        }
        // Final pass removes every remaining blue backdrop pixel, including enclosed areas.
        for (py in 0 until height) for (px in 0 until width) {
            val p = bitmap.getPixel(px, py)
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            if (b > r + 18 && b > g + 4 && r < 115 && g < 145) {
                bitmap.setPixel(px, py, Color.TRANSPARENT)
            }
        }
        return bitmap
    }

    private fun render() {
        window.statusBarColor = Color.rgb(13,27,47)
        window.navigationBarColor = Color.rgb(239,234,222)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10),dp(7),dp(10),0)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(239,246,244), Color.rgb(225,237,235), Color.rgb(248,245,239)))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brandMark = ImageView(this).apply {
            setImageBitmap(logoOnHeader())
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "لوگوی ملک‌یاب؛ خانه و ذره‌بین طلایی"
        }
        val logoWrap = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(13,27,47), Color.rgb(31,51,76), Color.rgb(13,27,47))).apply {
                cornerRadius = dp(25).toFloat()
                setStroke(dp(1), Color.rgb(190,151,82))
            }
            elevation = dp(5).toFloat()
            addView(brandMark, FrameLayout.LayoutParams(dp(120),dp(120),Gravity.CENTER))
        }
        page.addViewSafely(logoWrap, LinearLayout.LayoutParams(-1, dp(132)).apply {
            bottomMargin = dp(8); topMargin = dp(2)
        })

        val slogan = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(1), dp(2), dp(1))
            setBackgroundColor(Color.TRANSPARENT)
        }
        slogan.addViewSafely(text("ساماندهی هوشمند فایل‌های ملکی", 17f, navy, true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            includeFontPadding = false
            setPadding(0, dp(2), 0, dp(2))
        }, LinearLayout.LayoutParams(-1, dp(29)))
        slogan.addViewSafely(text("ثبت، دسته‌بندی و مدیریت آسان آگهی‌ها", 11f, muted).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            includeFontPadding = false
            setPadding(0, dp(1), 0, dp(1))
        }, LinearLayout.LayoutParams(-1, dp(21)))
        page.addViewSafely(slogan, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(8) })

        val searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9),dp(5),dp(9),dp(5))
            background = rounded(Color.WHITE,24,Color.rgb(196,218,213))
            elevation = dp(2).toFloat()
        }
        search = field("عنوان")
        search.background = rounded(Color.rgb(255,254,250),13,Color.rgb(232,224,207))
        search.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                searchSubmitted = true
                (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
                refreshList()
                true
            } else false
        }
        searchPanel.addViewSafely(search, LinearLayout.LayoutParams(-1, dp(48)))
        page.addViewSafely(searchPanel, LinearLayout.LayoutParams(-1, dp(60)).apply { bottomMargin = dp(9) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addViewSafely(list)
        search.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { searchSubmitted = false; refreshList() }
            override fun afterTextChanged(s: Editable?) {}
        })

        val addCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8),dp(3),dp(8),dp(3))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(13,27,47), Color.rgb(38,58,82), Color.rgb(13,27,47))
            ).apply { cornerRadius = dp(21).toFloat(); setStroke(dp(1), Color.rgb(205,166,91)) }
            elevation = dp(6).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { openEditorSafely(null) }
        }
        val plus = TextView(this).apply {
            text = "+"
            textSize = 26f
            setTextColor(Color.rgb(235,195,112))
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(53,72,96), 14, Color.rgb(190,155,85))
        }
        addCard.addViewSafely(plus, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
        val addText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        addText.addViewSafely(text("ثبت ملک جدید", 16f, Color.WHITE, true).apply {
            includeFontPadding = false
            setPadding(0, dp(2), 0, dp(2))
        }, LinearLayout.LayoutParams(-1, dp(25)))
        addText.addViewSafely(text("افزودن فایل و مشخصات ملک", 11f, Color.rgb(220,230,242)).apply {
            includeFontPadding = false
            setPadding(0, dp(2), 0, dp(2))
            maxLines = 1
        }, LinearLayout.LayoutParams(-1, dp(20)))
        addCard.addViewSafely(addText, LinearLayout.LayoutParams(0, -2, 1f))
        page.addViewSafely(addCard, LinearLayout.LayoutParams(-1, dp(78)).apply { bottomMargin = dp(13); topMargin = dp(2) })

        val categoryTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(1), dp(3), dp(3))
        }
        categoryTitle.addViewSafely(text("دسته‌بندی آگهی‌ها", 16f, navy, true), LinearLayout.LayoutParams(0,-2,1f))
        categoryTitle.addViewSafely(text("انتخاب بخش موردنظر", 11f, muted))
        page.addViewSafely(categoryTitle)
        val categories = listOf("فروش", "رهن", "رهن و اجاره", "کلنگی و مستغلات")
        categories.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { category ->
                val accent = when(category) {
                    "فروش" -> Color.rgb(194,111,72)
                    "رهن" -> Color.rgb(37,132,126)
                    "رهن و اجاره" -> Color.rgb(91,119,157)
                    else -> Color.rgb(125,104,82)
                }
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(5),dp(3),dp(5),dp(3))
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.WHITE, Color.rgb(235,245,242))).apply {
                        cornerRadius = dp(25).toFloat()
                        setStroke(dp(1), Color.rgb(193,216,209))
                    }
                    elevation = dp(5).toFloat()
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { activeSection = category; dealFilter = "همه"; renderSection() }
                }
                val symbol = when(category) {
                    "فروش" -> "↗"
                    "رهن" -> "⌂"
                    "رهن و اجاره" -> "⇄"
                    else -> "▦"
                }
                val iconCircle = TextView(this).apply {
                    text = symbol
                    textSize = 22f
                    setTextColor(accent)
                    gravity = Gravity.CENTER
                    background = rounded(Color.rgb(250,246,235), 30, accent)
                }
                tile.addViewSafely(iconCircle, LinearLayout.LayoutParams(dp(36), dp(36)).apply { bottomMargin = dp(7) })
                tile.addViewSafely(text(category, 16f, navy, true).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    maxLines = 2
                    minLines = 1
                    includeFontPadding = false
                    setPadding(dp(2), dp(3), dp(2), dp(3))
                })
                row.addViewSafely(tile, LinearLayout.LayoutParams(0,dp(112),1f).apply {
                    marginEnd = dp(5); marginStart = dp(5); bottomMargin = dp(8)
                })
            }
            page.addViewSafely(row)
        }

        scroll.addViewSafely(page)
        root.addViewSafely(scroll, LinearLayout.LayoutParams(-1,0,1f))
        val credit = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setBackgroundColor(Color.TRANSPARENT)
        }
        credit.addViewSafely(text("طراح: مهندس رئوف نژاد", 11f, navy, true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = true
            setPadding(0, 0, 0, 0)
            minHeight = dp(24)
        }, LinearLayout.LayoutParams(-1, -2))
        root.addViewSafely(credit, LinearLayout.LayoutParams(-1, dp(42)).apply {
            topMargin = dp(2); bottomMargin = dp(8)
        })
        setContentView(root)
        list.removeAllViews()
    }

    private fun renderSection() {
        val section = activeSection ?: return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(250,248,242), Color.rgb(239,233,219)))
        }
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(18,34,57), Color.rgb(38,57,78))).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), Color.rgb(195,157,91))
            }
            elevation = dp(4).toFloat()
        }
        toolbar.addViewSafely(button("‹ بازگشت", {
            activeSection = null
            render()
        }).apply {
            background = rounded(Color.rgb(250,246,235),12,Color.rgb(195,157,91))
            setTextColor(navy)
        }, LinearLayout.LayoutParams(-2, dp(44)))
        toolbar.addViewSafely(text(section, 19f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f).apply {
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
        toolbar.addViewSafely(modeButton, LinearLayout.LayoutParams(-2, dp(44)))
        page.addViewSafely(toolbar, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(24)
            bottomMargin = dp(14)
        })

        search = field("عنوان")
        search.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                searchSubmitted = true
                (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
                refreshList()
                true
            } else false
        }
        page.addViewSafely(search, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(12) })
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addViewSafely(results)
        scroll.addViewSafely(page)
        root.addViewSafely(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
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
            "تعداد طبقات ساختمان: " + p.buildingFloors, "طبقه ملک: " + p.floor,
            "تک‌واحدی: " + p.singleUnit,
            if (p.singleUnit == "خیر") "تعداد واحد: " + p.unitsPerFloor else "",
            "پارکینگ: " + p.parking, "آسانسور: " + p.elevator, "انباری: " + p.storage,
            "بالکن: " + p.balcony, "سیستم سرمایش: " + p.cooling, "سیستم گرمایش: " + p.heating,
            "جهت ملک: " + p.direction, "نمای ساختمان: " + p.facade, "حمام مستر: " + p.master,
            "سرویس بهداشتی: " + p.toilet, "کابینت: " + p.cabinet, "کف‌پوش: " + p.flooring,
            "توضیحات: " + p.notes, "تاریخ ثبت: " + persianDate(p.createdAt))
            .filter { it.substringAfter(": ").isNotBlank() }
            .forEach { content.addViewSafely(text(it,14f,navy)) }
        sc.addViewSafely(content); box.addViewSafely(sc)
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
            column.addViewSafely(text("تصویر " + (index + 1), 12f, muted))
            column.addViewSafely(image, LinearLayout.LayoutParams(-1, dp(210)).apply { bottomMargin = dp(12) })
        }
        scroll.addViewSafely(column)
        container.addViewSafely(scroll)
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
        content.addViewSafely(counter)
        content.addViewSafely(image, LinearLayout.LayoutParams(-1,dp(420)))
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
        // Do not show the entire database until the user actually starts searching.
        if (query.isBlank() && activeSection == null) return
        val allItems = db.all()
        val normalized = query.map { ch -> when(ch) {
            in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
            in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
            else -> ch
        } }.joinToString("").lowercase().replace("ي","ی").replace("ك","ک").trim()
        // Offer matching values from every saved property field as the user types.
        val suggestionValues = linkedSetOf<String>()
        allItems.forEach { p ->
            listOf(
                p.title,p.kind,p.deal,p.address,p.city,p.area,p.price,p.deposit,p.rent,
                p.unitCount,p.floor,p.buildingFloors,p.singleUnit,p.unitsPerFloor,p.parking,p.elevator,p.storage,p.balcony,p.cooling,p.heating,
                p.direction,p.facade,p.master,p.toilet,p.cabinet,p.flooring
            ).filter { it.isNotBlank() }.forEach { value ->
                val normalizedValue = value.lowercase().replace("ي","ی").replace("ك","ک")
                if (normalizedValue.contains(normalized)) suggestionValues.add(value.trim())
            }
        }
        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val suggestionScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(suggestions)
        }
        if (suggestionValues.isNotEmpty()) {
            list.addViewSafely(text("پیشنهادها",10f,muted,true))
            suggestionValues.take(10).forEach { value ->
                val chip = TextView(this).apply {
                    text = value
                    textSize = 10f
                    setTextColor(blue)
                    gravity = Gravity.CENTER
                    setPadding(dp(9),dp(7),dp(9),dp(7))
                    background = rounded(Color.WHITE,9,Color.rgb(220,227,236))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        search.setText(value)
                        search.setSelection(search.text.length)
                    }
                }
                suggestions.addViewSafely(chip,LinearLayout.LayoutParams(-2,-2).apply { marginEnd=dp(5) })
            }
            list.addViewSafely(suggestionScroll,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(5) })
        }
        // A number typed alone opens quick budget choices instead of searching for "2 toman".
        val bareNumberQuery = normalized.matches(Regex("""\d+"""))
        if (bareNumberQuery) {
            val digit = normalized.toLongOrNull() ?: return
            val scales = listOf(1_000_000L,10_000_000L,100_000_000L,1_000_000_000L,10_000_000_000L)
            val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
            list.addViewSafely(text("جست‌وجو بر اساس بودجه",10f,muted,true))
            list.addViewSafely(horizontal,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(5) })
            scales.forEach { scale ->
                val amount = if (digit > Long.MAX_VALUE / scale) Long.MAX_VALUE else digit * scale
                val label = when {
                    amount >= 1_000_000_000L -> {
                        val whole = amount / 1_000_000_000L
                        val remainder = amount % 1_000_000_000L
                        val decimal = if (remainder == 0L) "" else "." + (remainder / 100_000_000L).toString()
                        formatMoney(whole.toString()) + decimal + " میلیارد"
                    }
                    amount % 1_000_000L == 0L -> formatMoney((amount / 1_000_000L).toString()) + " میلیون"
                    else -> formatMoney(amount.toString())
                }
                val chip = TextView(this).apply {
                    text = label; textSize = 10f; setTextColor(blue); gravity = Gravity.CENTER
                    setPadding(dp(9),dp(7),dp(9),dp(7))
                    background = rounded(Color.WHITE,9,Color.rgb(220,227,236))
                    isClickable = true; isFocusable = true
                    setOnClickListener { search.setText(label); search.setSelection(search.text.length) }
                }
                chips.addViewSafely(chip,LinearLayout.LayoutParams(-2,-2).apply { marginEnd=dp(5) })
            }
        }
        // Parse an optional deal prefix first, then search all remaining terms and amounts.
        val queryBody = when {
            normalized.startsWith("رهن و اجاره") -> normalized.removePrefix("رهن و اجاره").trim()
            normalized.startsWith("فروش") -> normalized.removePrefix("فروش").trim()
            normalized.startsWith("اجاره") -> normalized.removePrefix("اجاره").trim()
            normalized.startsWith("رهن") -> normalized.removePrefix("رهن").trim()
            else -> normalized
        }
        val explicitDeal = when {
            normalized.startsWith("فروش") -> "فروش"
            normalized.startsWith("رهن و اجاره") || normalized.startsWith("اجاره") -> "رهن و اجاره"
            normalized.startsWith("رهن") -> "رهن کامل"
            else -> null
        }
        val amountPattern = Regex("""(?<!\d)(\d{1,3}(?:[.,٬]\d{3})+|\d+(?:[.,]\d+)?)\s*(میلیارد|میلیون|هزار)?""")
        val amountTokens = amountPattern.findAll(queryBody).toList()
        val textWords = queryBody.replace(amountPattern, " ")
            .split(Regex("""[\s,،٬]+"""))
            .filter { it.isNotBlank() && it !in listOf("تا","حدود","قیمت","متر","متراژ","تومان","تومن","میلیارد","میلیون","هزار","پیش") }
        val bareBudget = queryBody.matches(Regex("""\d+"""))
        val bareBudgetValue = if (bareBudget) queryBody.toLongOrNull() else null
        val found = allItems.filter { p ->
            val searchable = listOf(
                p.title,p.notes,p.kind,p.deal,p.address,p.city,p.area,p.price,p.deposit,p.rent,
                p.unitCount,p.floor,p.buildingFloors,p.singleUnit,p.unitsPerFloor,p.parking,p.elevator,p.storage,p.balcony,p.cooling,p.heating,
                p.direction,p.facade,p.master,p.toilet,p.cabinet,p.flooring
            ).joinToString(" ").lowercase().replace("ي","ی").replace("ك","ک")
            val textOk = textWords.all { word ->
                searchable.contains(word) || (word == "پیش" && p.deal != "فروش")
            }
            val dealOk = when (explicitDeal) {
                "فروش" -> p.deal == "فروش"
                "رهن کامل" -> p.deal == "رهن کامل"
                "رهن و اجاره" -> p.deal == "رهن و اجاره"
                else -> true
            }
            fun relevantMoneyValues(): List<Long> = when (p.deal) {
                "فروش" -> listOf(rawMoney(p.price).toLongOrNull() ?: 0L)
                "رهن کامل" -> listOf(rawMoney(p.deposit).toLongOrNull() ?: 0L)
                else -> listOf(rawMoney(p.deposit).toLongOrNull() ?: 0L, rawMoney(p.rent).toLongOrNull() ?: 0L)
            }.filter { it > 0L }
            val numberOk = when {
                bareBudgetValue != null -> {
                    val digit = bareBudgetValue
                    val budget = if (digit > Long.MAX_VALUE / 1_000_000_000L) Long.MAX_VALUE else digit * 1_000_000_000L
                    relevantMoneyValues().any { it <= budget } ||
                        (explicitDeal == null && listOf(p.area,p.floor,p.buildingFloors,p.unitsPerFloor,p.unitCount).any { rawMoney(it).contains(queryBody) })
                }
                amountTokens.isNotEmpty() -> amountTokens.all { token ->
                    val numericText = token.groupValues[1].replace(",", ".").replace("٬", "")
                    val numeric = numericText.toDoubleOrNull() ?: 0.0
                    val multiplier = when (token.groupValues[2]) {
                        "میلیارد" -> 1_000_000_000.0
                        "میلیون" -> 1_000_000.0
                        "هزار" -> 1_000.0
                        else -> 1.0
                    }
                    val budget = numeric * multiplier
                    val before = queryBody.substring(0, token.range.first)
                    val values = when {
                        before.contains("اجاره") -> listOf(rawMoney(p.rent).toLongOrNull() ?: 0L)
                        before.contains("رهن") || before.contains("پیش") -> listOf(rawMoney(p.deposit).toLongOrNull() ?: 0L)
                        before.contains("فروش") || before.contains("قیمت") -> listOf(rawMoney(p.price).toLongOrNull() ?: 0L)
                        else -> relevantMoneyValues()
                    }.filter { it > 0L }
                    val isCeiling = multiplier > 1.0 || before.contains("تا") || (multiplier == 1.0 && numericText.length <= 3)
                    values.any { value ->
                        (if (isCeiling) value.toDouble() <= budget else value.toDouble() == budget) ||
                            (explicitDeal == null && value.toString().contains(numericText.replace(".", "")))
                    }
                }
                else -> true
            }
            textOk && dealOk && numberOk
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
            if (query.isNotBlank()) list.addViewSafely(text("نتیجه‌ای برای این جست‌وجو پیدا نشد.", 14f, muted))
            else if (section != null) list.addViewSafely(text("در بخش «" + section + "» هنوز فایلی ثبت نشده است.", 14f, muted))
            return
        }
        if (query.isNotBlank()) {
            if (!searchSubmitted) {
                list.addViewSafely(text("پیش‌نمایش فایل‌های پیشنهادی — برای دیدن همه، دکمه جست‌وجو روی صفحه‌کلید را بزن", 10f, muted, true))
                items.take(8).forEach { p ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8),dp(6),dp(8),dp(6)); background = rounded(Color.WHITE,13,Color.rgb(222,211,188)); isClickable = true; isFocusable = true }
                    val photoPath = try { val a=JSONArray(p.photos); if(a.length()>0) a.getString(0) else "" } catch (_:Exception) { "" }
                    val thumb = ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; background=rounded(pale,9); if(photoPath.isNotBlank()) try { setImageURI(Uri.parse(photoPath)) } catch (_:Exception) {} }
                    row.addViewSafely(thumb,LinearLayout.LayoutParams(dp(54),dp(54)).apply { marginEnd=dp(9) })
                    val details = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                    details.addViewSafely(text(p.title.ifBlank { "بدون عنوان" },12f,navy,true).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END })
                    val money = when(p.deal) { "فروش" -> "فروش: "+formatMoney(p.price); "رهن کامل" -> "رهن: "+formatMoney(p.deposit); else -> "رهن "+formatMoney(p.deposit)+" | اجاره "+formatMoney(p.rent) }
                    details.addViewSafely(text(money,10f,blue,true).apply { maxLines=1 })
                    details.addViewSafely(text(p.kind+" • "+p.area+" متر",9f,muted).apply { maxLines=1 })
                    row.addViewSafely(details,LinearLayout.LayoutParams(0,-2,1f))
                    row.addViewSafely(text("›",22f,gold,true))
                    row.setOnClickListener { showPropertyDetails(p) }
                    list.addViewSafely(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) })
                }
                if(items.isEmpty()) list.addViewSafely(text("موردی مطابق عبارت واردشده پیدا نشد.",12f,muted))
                return
            }
            list.addViewSafely(text("نتایج جست‌وجو • "+items.size+" فایل",13f,navy,true))
            items.chunked(2).forEach { pair ->
                val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.TOP }
                pair.forEach { p ->
                    val card = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8)); background=rounded(Color.WHITE,14,Color.rgb(222,211,188)); elevation=dp(2).toFloat(); isClickable=true; isFocusable=true; setOnClickListener { showPropertyDetails(p) } }
                    val photoPath = try { val a=JSONArray(p.photos); if(a.length()>0) a.getString(0) else "" } catch (_:Exception) { "" }
                    val thumb = ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; background=rounded(pale,9); if(photoPath.isNotBlank()) try { setImageURI(Uri.parse(photoPath)) } catch (_:Exception) {} }
                    card.addViewSafely(thumb,LinearLayout.LayoutParams(-1,dp(96)))
                    card.addViewSafely(text(p.title.ifBlank { "بدون عنوان" },12f,navy,true).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END })
                    card.addViewSafely(text(p.deal+" • "+p.kind,9f,muted).apply { maxLines=1 })
                    val money = when(p.deal) { "فروش" -> "قیمت: "+formatMoney(p.price); "رهن کامل" -> "رهن: "+formatMoney(p.deposit); else -> "رهن: "+formatMoney(p.deposit)+"\nاجاره: "+formatMoney(p.rent) }
                    card.addViewSafely(text(money,10f,blue,true).apply { maxLines=2 })
                    card.addViewSafely(text("متراژ: "+p.area+" متر",10f,navy))
                    if(p.deal=="فروش") { val sqm=rawMoney(p.area).toLongOrNull()?:0L; val price=rawMoney(p.price).toLongOrNull()?:0L; if(sqm>0&&price>0) card.addViewSafely(text("هر متر: "+formatMoney((price/sqm).toString()),9f,gold,true)) }
                    card.addViewSafely(text("تک‌واحدی: "+p.singleUnit,9f,muted))
                    card.addViewSafely(text("پارکینگ: "+p.parking+" • آسانسور: "+p.elevator,9f,muted).apply { maxLines=2 })
                    row.addViewSafely(card,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(4); marginEnd=dp(4); bottomMargin=dp(8) })
                }
                if(pair.size==1) row.addViewSafely(View(this),LinearLayout.LayoutParams(0,1,1f))
                list.addViewSafely(row,LinearLayout.LayoutParams(-1,-2))
            }
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
                grid.addViewSafely(card, lp)
            }
            list.addViewSafely(grid)
        } else {
            items.forEach { p ->
                val card = makeCard(p, false)
                list.addViewSafely(card, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(9) })
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
            card.addViewSafely(thumb, LinearLayout.LayoutParams(-1, dp(if (compact) 58 else 115)))
        } else if (compact) {
            card.addViewSafely(text("بدون عکس", 10f, muted))
        }
        card.addViewSafely(text(p.title.ifBlank { "بدون عنوان" }, if (compact) 11f else 17f, navy, true))
        card.addViewSafely(text(p.deal, if (compact) 10f else 12f, blue, true))
        card.addViewSafely(text(p.kind, if (compact) 9f else 13f, muted))
        card.addViewSafely(text(p.area + " متر", if (compact) 9f else 12f, muted))
        if (p.address.isNotBlank()) card.addViewSafely(text("آدرس: " + p.address, if (compact) 8f else 11f, muted))
        val sqm = rawMoney(p.area).toLongOrNull() ?: 0L
        val salePrice = p.price.toLongOrNull() ?: 0L
        if (p.deal == "فروش" && sqm > 0 && salePrice > 0) {
            card.addViewSafely(text("هر متر: " + formatMoney((salePrice / sqm).toString()) + " تومان", if (compact) 8f else 11f, Color.rgb(174,133,59), true))
        }
        val money = when (p.deal) {
            "فروش" -> "قیمت: " + formatMoney(p.price)
            "رهن کامل" -> "رهن: " + formatMoney(p.deposit)
            else -> "رهن: " + formatMoney(p.deposit) + "\nاجاره: " + formatMoney(p.rent)
        }
        card.addViewSafely(text(money, if (compact) 9f else 13f, navy, true))
        card.addViewSafely(text("تاریخ ثبت: " + persianDate(p.createdAt), if (compact) 8f else 10f, muted).apply { gravity = Gravity.LEFT })
        if (photoCount > 0) card.addViewSafely(button("عکس‌ها " + photoCount, { showGallery(p) }))
        val actions = LinearLayout(this).apply { orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        actions.addViewSafely(button("ویرایش", { showEditor(p) }), LinearLayout.LayoutParams(if (compact) -1 else 0, dp(38), if (compact) 0f else 1f))
        actions.addViewSafely(button("حذف", {
            AlertDialog.Builder(this).setMessage("این ملک حذف شود؟")
                .setNegativeButton("انصراف",null).setPositiveButton("حذف") { _,_ -> db.delete(p.id); refreshList() }.show()
        }), LinearLayout.LayoutParams(if (compact) -1 else 0, dp(38), if (compact) 0f else 1f))
        card.addViewSafely(actions)
        card.setOnClickListener { showPropertyDetails(p) }
        return card
    }

    private fun openEditorSafely(existing: Property?) {
        try {
            showEditor(existing)
        } catch (e: Exception) {
            android.util.Log.e("MelkYab", "Unable to open property editor", e)
            AlertDialog.Builder(this)
                .setTitle("خطا در باز کردن فرم ملک")
                .setMessage("فرم باز نشد. لطفاً از این پیام عکس بگیر و بفرست تا علت دقیق خطا را برطرف کنم.\n\n" + (e.localizedMessage ?: e.javaClass.simpleName))
                .setPositiveButton("باشه", null)
                .show()
        }
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
        content.addViewSafely(text("۱. نوع معامله را انتخاب کنید", 14f, navy, true))
        val deals = listOf("فروش","رهن کامل","رهن و اجاره")
        val dealSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, deals)
            setSelection(deals.indexOf(existing?.deal).takeIf { it >= 0 } ?: 0)
        }
        content.addViewSafely(dealSpinner)
        content.addViewSafely(text("۲. مشخصات ملک", 14f, navy, true))
        val kinds = listOf("آپارتمان","خانه و ویلایی","کلنگی و مستغلات","مغازه و تجاری")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, kinds)
            setSelection(kinds.indexOf(existing?.kind).takeIf { it >= 0 } ?: 0)
        }
        content.addViewSafely(text("نوع ملک")); content.addViewSafely(spinner)
        val title = field("عنوان ملک",existing?.title ?: "")
        val address = field("آدرس ملک",existing?.address ?: "")
        val area = field("متراژ (متر)",existing?.area ?: "").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val price = moneyField("قیمت کل (تومان)",existing?.price ?: "")
        val deposit = moneyField("مبلغ رهن (تومان)",existing?.deposit ?: "")
        val rent = moneyField("اجاره ماهانه (تومان)",existing?.rent ?: "")
        val notes = field("توضیحات",existing?.notes ?: "").apply { minLines = 3; gravity = Gravity.TOP }

        fun option(label: String, values: List<String>, current: String): Spinner {
            content.addViewSafely(text(label, 12f, muted, true))
            return Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values)
                setSelection(values.indexOf(current).takeIf { it >= 0 } ?: 0)
            }.also { content.addViewSafely(it) }
        }
        content.addViewSafely(title); content.addViewSafely(address); content.addViewSafely(area)
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
            val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            block.addViewSafely(text("مبالغ پیشنهادی $fieldName",9f,muted,true))
            val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val suggestions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            horizontal.addViewSafely(suggestions)
            block.addViewSafely(horizontal, LinearLayout.LayoutParams(-1,-2))
            content.addViewSafely(block, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(5) })
            target.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val typed = rawMoney(s?.toString() ?: "").toLongOrNull() ?: 0L
                    suggestions.removeAllViews()
                    if (typed <= 0L) { block.visibility = View.GONE; return }
                    block.visibility = View.VISIBLE
                    val scales = listOf(1_000_000L,10_000_000L,100_000_000L,1_000_000_000L,10_000_000_000L)
                    scales.forEach { scale ->
                        val amount = if (typed > Long.MAX_VALUE / scale) Long.MAX_VALUE else typed * scale
                        val label = when {
                            amount >= 1_000_000_000L -> {
                                val billions = amount / 1_000_000_000L
                                val remainder = amount % 1_000_000_000L
                                val decimal = if (remainder == 0L) "" else "." + (remainder / 100_000_000L).toString()
                                formatMoney(billions.toString()) + decimal + " میلیارد"
                            }
                            amount % 1_000_000L == 0L -> formatMoney((amount / 1_000_000L).toString()) + " میلیون"
                            else -> formatMoney(amount.toString())
                        }
                        val chip = TextView(this@MainActivity).apply {
                            text = label
                            textSize = 10f
                            setTextColor(blue)
                            gravity = Gravity.CENTER
                            setPadding(dp(8),dp(5),dp(8),dp(5))
                            background = rounded(Color.WHITE,8,Color.rgb(220,227,236))
                            isClickable = true
                            isFocusable = true
                            setOnClickListener {
                                target.setText(formatMoney(amount.toString()))
                                target.setSelection(target.text.length)
                            }
                        }
                        suggestions.addViewSafely(chip,LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(5) })
                    }
                }
            })
        }
        content.addViewSafely(priceLabel)
        content.addViewSafely(price)
        attachAmountSuggestions(price,"قیمت")
        content.addViewSafely(pricePerMeter)
        content.addViewSafely(depositLabel)
        content.addViewSafely(deposit)
        attachAmountSuggestions(deposit,"رهن")
        content.addViewSafely(rentLabel)
        content.addViewSafely(rent)
        attachAmountSuggestions(rent,"اجاره")

        content.addViewSafely(text("۳. جزئیات ساختمان و امکانات", 14f, navy, true))
        val buildingFloors = field("تعداد طبقات ساختمان", existing?.buildingFloors ?: "").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val floor = field("طبقه ملک", existing?.floor ?: "").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        content.addViewSafely(buildingFloors)
        content.addViewSafely(floor)
        content.addViewSafely(text("تک‌واحدی؟", 12f, muted, true))
        val singleUnitGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        val singleYes = RadioButton(this).apply { text = "بله"; textSize = 14f; isChecked = (existing?.singleUnit ?: "بله") == "بله" }
        val singleNo = RadioButton(this).apply { text = "خیر"; textSize = 14f; isChecked = existing?.singleUnit == "خیر" }
        singleUnitGroup.addView(singleYes)
        singleUnitGroup.addView(singleNo)
        content.addViewSafely(singleUnitGroup, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        val unitsLabel = text("تعداد واحد در هر طبقه", 12f, muted, true)
        val unitsPerFloor = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("۲","۳","۴","۵"))
            setSelection(listOf("۲","۳","۴","۵").indexOf(existing?.unitsPerFloor).takeIf { it >= 0 } ?: 0)
        }
        fun updateUnitsVisibility() {
            val visible = singleNo.isChecked
            unitsLabel.visibility = if (visible) View.VISIBLE else View.GONE
            unitsPerFloor.visibility = if (visible) View.VISIBLE else View.GONE
        }
        singleUnitGroup.setOnCheckedChangeListener { _, _ -> updateUnitsVisibility() }
        content.addViewSafely(unitsLabel)
        content.addViewSafely(unitsPerFloor)
        updateUnitsVisibility()
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
        content.addViewSafely(text("تصاویر ملک", 14f, navy, true))
        val photoStatus = text("عکس‌های انتخاب‌شده: " + selectedPhotos.size, 12f, muted)
        content.addViewSafely(photoStatus)
        content.addViewSafely(button("انتخاب عکس از گوشی", { choosePhotos(); photoStatus.text = "برای به‌روزرسانی تعداد، دوباره فرم را باز کنید." }))
        content.addViewSafely(button("مشاهده عکس‌های انتخاب‌شده", { showSelectedGallery() }))
        content.addViewSafely(text("توضیحات", 12f, muted, true)); content.addViewSafely(notes)
        scroll.addViewSafely(content); wrap.addViewSafely(scroll)
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
                        "",floor.text.toString(),
                        parking.selectedItem.toString(),elevator.selectedItem.toString(),
                        storage.selectedItem.toString(),balcony.selectedItem.toString(),
                        cooling.selectedItem.toString(),heating.selectedItem.toString(),
                        direction.selectedItem.toString(),facade.selectedItem.toString(),
                        master.selectedItem.toString(),toilet.selectedItem.toString(),
                        cabinet.selectedItem.toString(),flooring.selectedItem.toString(),
                        existing?.createdAt ?: System.currentTimeMillis(), address.text.toString(),
                        buildingFloors.text.toString(), if (singleNo.isChecked) "خیر" else "بله",
                        if (singleNo.isChecked) unitsPerFloor.selectedItem.toString() else "۱"))
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
            row.addViewSafely(img, LinearLayout.LayoutParams(dp(90),dp(75)))
            row.addViewSafely(text("تصویر " + (index+1),13f), LinearLayout.LayoutParams(0,-2,1f))
            row.addViewSafely(button("حذف", { selectedPhotos.remove(path); Toast.makeText(this,"عکس از فهرست انتخاب‌ها حذف شد؛ فرم را دوباره باز کن.",Toast.LENGTH_SHORT).show() }))
            col.addViewSafely(row)
        }
        sc.addViewSafely(col); box.addViewSafely(sc)
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
