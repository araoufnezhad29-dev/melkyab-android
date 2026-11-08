package ir.melkyab.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray

data class Property(
    val id: Long, val kind: String, val title: String, val city: String,
    val area: String, val price: String, val deposit: String, val rent: String,
    val notes: String, val photos: String
)

class PropertyDb(context: Context) : android.database.sqlite.SQLiteOpenHelper(context, "melkyab.db", null, 1) {
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("""CREATE TABLE properties(
            id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, title TEXT, city TEXT,
            area TEXT, price TEXT, deposit TEXT, rent TEXT, notes TEXT, photos TEXT)""")
    }
    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun all(query: String = ""): List<Property> {
        val result = mutableListOf<Property>()
        val c = readableDatabase.rawQuery(
            "SELECT * FROM properties WHERE title LIKE ? OR city LIKE ? OR notes LIKE ? ORDER BY id DESC",
            arrayOf("%$query%", "%$query%", "%$query%")
        )
        c.use {
            while (it.moveToNext()) result.add(Property(
                it.getLong(0), it.getString(1) ?: "", it.getString(2) ?: "",
                it.getString(3) ?: "", it.getString(4) ?: "", it.getString(5) ?: "",
                it.getString(6) ?: "", it.getString(7) ?: "", it.getString(8) ?: "",
                it.getString(9) ?: "[]"
            ))
        }
        return result
    }
    fun save(p: Property) {
        val v = ContentValues().apply {
            put("kind",p.kind); put("title",p.title); put("city",p.city); put("area",p.area)
            put("price",p.price); put("deposit",p.deposit); put("rent",p.rent)
            put("notes",p.notes); put("photos",p.photos)
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
    private fun choosePhotos() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 101)
    }

    @Deprecated("Legacy activity result API for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == Activity.RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) addPhoto(clip.getItemAt(i).uri)
            } else data.data?.let { addPhoto(it) }
            Toast.makeText(this, "تعداد عکس‌ها: ${selectedPhotos.size}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addPhoto(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        if (!selectedPhotos.contains(uri.toString())) selectedPhotos.add(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = PropertyDb(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 15f): TextView = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(0xFF202124.toInt())
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    private fun field(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint; setText(value); textSize = 15f
        setSingleLine(hint != "توضیحات")
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; setOnClickListener { action() }
    }
    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(8)); setBackgroundColor(0xFFF7F7F7.toInt()) }
        root.addView(text("ملک‌یاب", 25f))
        root.addView(text("مدیریت فایل املاک | قیمت به تومان", 13f))
        search = field("جست‌وجو در عنوان، شهر و توضیحات")
        root.addView(search)
        search.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshList() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        root.addView(button("+ ثبت ملک جدید") { showEditor(null) })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        refreshList()
    }

    private fun refreshList() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val items = db.all(if (::search.isInitialized) search.text.toString() else "")
        if (items.isEmpty()) list.addView(text("هنوز ملکی ثبت نشده است. از دکمه بالا شروع کن."))
        items.forEach { p ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10),dp(8),dp(10),dp(8))
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            card.addView(text(p.title.ifBlank { "بدون عنوان" },18f))
            card.addView(text("${p.kind}  •  ${p.city}  •  ${p.area} متر"))
            val money = if (p.price.isNotBlank()) "قیمت: ${p.price} تومان" else "ودیعه: ${p.deposit} تومان  |  اجاره: ${p.rent} تومان"
            card.addView(text(money))
            if (p.photos != "[]" && p.photos.isNotBlank()) {
                val count = try { JSONArray(p.photos).length() } catch (_: Exception) { 0 }
                card.addView(text("📷 $count عکس"))
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(button("ویرایش") { showEditor(p) })
            actions.addView(button("حذف") {
                AlertDialog.Builder(this).setMessage("این ملک حذف شود؟")
                    .setNegativeButton("انصراف",null).setPositiveButton("حذف") { _,_ -> db.delete(p.id); refreshList() }.show()
            })
            card.addView(actions)
            val lp = LinearLayout.LayoutParams(-1,-2); lp.bottomMargin = dp(10)
            list.addView(card,lp)
        }
    }

    private fun showEditor(existing: Property?) {
        selectedPhotos = try { if (existing == null) mutableListOf() else {
            val a=JSONArray(existing.photos); MutableList(a.length()){i->a.getString(i)}
        } } catch (_: Exception) { mutableListOf() }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(8),dp(18),dp(8)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val kinds = listOf("آپارتمان","خانه و ویلایی","زمین و کلنگی","مغازه و تجاری")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, kinds)
            setSelection(kinds.indexOf(existing?.kind).takeIf { it >= 0 } ?: 0)
        }
        content.addView(text("نوع ملک")); content.addView(spinner)
        val title = field("عنوان ملک",existing?.title ?: "")
        val city = field("شهر و محله",existing?.city ?: "")
        val area = field("متراژ (متر)",existing?.area ?: "")
        val price = field("قیمت کل (تومان) - برای فروش",existing?.price ?: "")
        val deposit = field("ودیعه (تومان) - برای اجاره",existing?.deposit ?: "")
        val rent = field("اجاره ماهانه (تومان)",existing?.rent ?: "")
        val notes = field("توضیحات",existing?.notes ?: "").apply { minLines = 3; gravity = Gravity.TOP }
        listOf(title,city,area,price,deposit,rent,notes).forEach { content.addView(it) }
        content.addView(button("انتخاب عکس‌ها از گوشی") { choosePhotos() })
        content.addView(text("عکس‌های انتخاب‌شده: ${selectedPhotos.size}"))
        scroll.addView(content); wrap.addView(scroll)
        AlertDialog.Builder(this).setTitle(if(existing==null) "ثبت ملک" else "ویرایش ملک")
            .setView(wrap)
            .setNegativeButton("انصراف",null)
            .setPositiveButton("ذخیره") { _,_ ->
                if (title.text.toString().isBlank()) {
                    Toast.makeText(this,"عنوان ملک را وارد کن",Toast.LENGTH_SHORT).show()
                } else {
                    val photos = JSONArray().apply { selectedPhotos.forEach { put(it) } }.toString()
                    db.save(Property(existing?.id ?: 0L,spinner.selectedItem.toString(),
                        title.text.toString(),city.text.toString(),area.text.toString(),
                        price.text.toString(),deposit.text.toString(),rent.text.toString(),
                        notes.text.toString(),photos))
                    refreshList()
                }
            }.show()
    }

    override fun onDestroy() { db.close(); super.onDestroy() }
}
