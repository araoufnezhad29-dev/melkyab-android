package ir.melkyab.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.graphics.Color
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class OnlineAdWebViewActivity : Activity() {
    companion object {
        const val EXTRA_URL = "online_ad_url"
        const val EXTRA_KASHANO_IMPORT = "kashano_import_mode"
        const val RESULT_KASHANO_ADS = "kashano_extracted_ads"
    }
    private lateinit var webView: WebView
    private var kashanoImportMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_URL).orEmpty()
        val uri = runCatching { android.net.Uri.parse(target) }.getOrNull()
        if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            finish()
            return
        }
        kashanoImportMode = intent.getBooleanExtra(EXTRA_KASHANO_IMPORT, false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.rgb(245, 248, 251))
        }
        val back = Button(this).apply {
            text = "بازگشت"
            setOnClickListener { if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else finish() }
        }
        val title = TextView(this).apply {
            text = "مشاهده آگهی اصلی"
            textSize = 15f
            setTextColor(Color.rgb(28, 47, 69))
            gravity = Gravity.CENTER
        }
        header.addView(back, LinearLayout.LayoutParams(-2, -2))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        if (kashanoImportMode) {
            header.addView(Button(this).apply {
                text = "استخراج نتایج"
                textSize = 12f
                isAllCaps = false
                setOnClickListener { extractKashanoResults() }
            }, LinearLayout.LayoutParams(-2, -2))
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        webView = WebView(this).apply {
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            isFocusableInTouchMode = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val next = request.url
                return next.scheme !in listOf("http", "https")
            }
        }
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        root.addView(progress, LinearLayout.LayoutParams(-1, 3))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        webView.loadUrl(uri.toString())
    }

    private fun extractKashanoResults() {
        val script = """
            (function() {
              try {
                const norm = s => (s || '').replace(/\s+/g, ' ').trim();
                const anchors = Array.from(document.querySelectorAll('a[href]'));
                const found = [];
                const seen = new Set();
                for (const a of anchors) {
                  let href;
                  try { href = new URL(a.getAttribute('href'), location.href).href; } catch(e) { continue; }
                  if (!/kashano\.ir$/i.test(new URL(href).hostname)) continue;
                  if (href === location.href || /#|javascript:/i.test(href)) continue;
                  let node = a;
                  let best = null;
                  for (let depth = 0; depth < 6 && node; depth++, node = node.parentElement) {
                    const t = norm(node.innerText || node.textContent);
                    if (t.length >= 25 && t.length <= 1400 && /(متر|متری|تومان|خواب|فروش|رهن|اجاره)/.test(t)) {
                      best = node;
                      if (t.length >= 60) break;
                    }
                  }
                  const text = norm((best || a).innerText || (best || a).textContent);
                  if (!text || text.length < 18 || !/(متر|متری|تومان|خواب|فروش|رهن|اجاره)/.test(text)) continue;
                  if (seen.has(href)) continue;
                  seen.add(href);
                  const img = (best || a).querySelector('img');
                  const titleNode = (best || a).querySelector('h1,h2,h3,h4,[class*=title],[class*=Title]');
                  const title = norm(titleNode ? titleNode.innerText : (a.innerText || text.split(' ').slice(0,12).join(' ')));
                  found.push({title:title.slice(0,180), summary:text.slice(0,700), price:(text.match(/[\d۰-۹٠-٩][\d۰-۹٠-٩.,٬،\s]*(?:میلیارد|میلیون)?\s*تومان/) || [''])[0], image:img ? (img.currentSrc || img.src || '') : '', url:href});
                  if (found.length >= 80) break;
                }
                return JSON.stringify({url:location.href, items:found});
              } catch(e) { return JSON.stringify({error:String(e),url:location.href,items:[]}); }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { value ->
            try {
                val decoded = JSONTokener(value ?: "null").nextValue() as? String
                val result = JSONObject(decoded ?: "{}")
                val items = result.optJSONArray("items") ?: JSONArray()
                if (items.length() == 0) {
                    Toast.makeText(this, "آگهی‌ای در صفحه شناسایی نشد؛ ابتدا فهرست نتایج کاشانو را باز کن و دوباره بزن.", Toast.LENGTH_LONG).show()
                    return@evaluateJavascript
                }
                val payload = Intent().putExtra(RESULT_KASHANO_ADS, items.toString())
                setResult(Activity.RESULT_OK, payload)
                Toast.makeText(this, items.length().toString() + " آگهی استخراج شد", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "خواندن نتایج صفحه ممکن نشد: " + (e.localizedMessage ?: "خطای نامشخص"), Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
