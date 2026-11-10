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
              const clean = s => (s || '').replace(/[\u200c\u200f\u202a-\u202e]/g, ' ').replace(/\s+/g, ' ').trim();
              const propertyWords = /(متر|متری|خواب|خوابه|فروش|رهن|اجاره|آپارتمان|خانه|ملک|ویلایی|ویلا|مسکونی|تجاری|واحد|طبقه|ودیعه|قیمت)/i;
              const priceWords = /(تومان|میلیارد|میلیون|ودیعه|قیمت|پیش)/i;
              const numeric = /[0-9۰-۹٠-٩][0-9۰-۹٠-٩.,٬،\s]*(?:تومان|میلیارد|میلیون)/i;
              const excluded = /(^|\/)(contact|contact-us|about|about-us|privacy|privacy-policy|terms|terms-and-conditions|rules|login|register|signup|faq|help|blog|news|advertise|advertising|support)(\/|$)/i;
              const selectors = 'a[href], [onclick], [ng-click], [role="link"], article, li, [class*="card"], [class*="Card"], [class*="item"], [class*="Item"], [class*="listing"], [class*="property"], [class*="estate"], [class*="result"], [data-id]';
              const seeds = Array.from(document.querySelectorAll(selectors));
              const found = [], seen = new Set();
              let anchorsSeen = 0, candidatesSeen = 0;
              function getUrl(node) {
                const a = node.matches && node.matches('a[href]') ? node : node.querySelector('a[href]');
                let raw = a ? a.getAttribute('href') : '';
                if (!raw) {
                  const click = (node.getAttribute && (node.getAttribute('onclick') || node.getAttribute('ng-click'))) || '';
                  const m = click.match(/(?:location\.href|window\.open|navigateTo|goTo)\s*\(?\s*['"]([^'"]+)['"]/i);
                  if (m) raw = m[1];
                }
                if (!raw || raw.startsWith('#') || /^javascript:/i.test(raw)) return '';
                try {
                  const u = new URL(raw, location.href);
                  if (!/^https?:$/.test(u.protocol) || !/(^|\.)kashano\.ir$/i.test(u.hostname) || excluded.test(u.pathname)) return '';
                  if (u.pathname === '/' || u.pathname === '') return '';
                  return u.href.split('#')[0];
                } catch(e) { return ''; }
              }
              for (const seed of seeds) {
                if (seed.matches('a[href]')) anchorsSeen++;
                let card = null, node = seed;
                for (let depth=0; node && depth<7; depth++, node=node.parentElement) {
                  const t = clean(node.innerText || node.textContent);
                  if (t.length < 28 || t.length > 1100) continue;
                  // Accept listing-like cards only: generic menu/navigation text often contains
                  // words such as "فروش" or "قیمت" but has no area/price/property combination.
                  const hasArea = /[0-9۰-۹٠-٩][0-9۰-۹٠-٩.,٬،]*\\s*(?:متر|متری)/i.test(t);
                  const hasPrice = numeric.test(t);
                  const hasRooms = /[0-9۰-۹٠-٩]+\\s*(?:خوابه|خواب)/i.test(t) || /بدون\\s*خواب/i.test(t);
                  const hasDeal = /(فروش|رهن|اجاره|ودیعه)/i.test(t);
                  const hasType = /(آپارتمان|خانه|ملک|ویلایی|ویلا|مسکونی|تجاری)/i.test(t);
                  const score = (hasArea ? 3 : 0) + (hasPrice ? 3 : 0) + (hasRooms ? 2 : 0) + (hasDeal ? 1 : 0) + (hasType ? 2 : 0);
                  if (score < 5 || (!hasArea && !(hasPrice && hasRooms && hasType))) continue;
                  const u = getUrl(node);
                  if (!u) continue;
                  const visual = node.querySelector('img, picture, h1, h2, h3, h4') || node.matches('article,li,[class*="card"],[class*="item"],[class*="listing"],[class*="property"],[class*="estate"]');
                  if (visual || node.matches('a[href],[onclick],[ng-click],[role="link"]')) { card=node; break; }
                }
                if (!card) continue;
                candidatesSeen++;
                const url = getUrl(card);
                if (!url || seen.has(url)) continue;
                const bodyText = clean(card.innerText || card.textContent);
                if (bodyText.length < 28 || bodyText.length > 1100) continue;
                const hasArea = /[0-9۰-۹٠-٩][0-9۰-۹٠-٩.,٬،]*\\s*(?:متر|متری)/i.test(bodyText);
                const hasPrice = numeric.test(bodyText);
                const hasRooms = /[0-9۰-۹٠-٩]+\\s*(?:خوابه|خواب)/i.test(bodyText) || /بدون\\s*خواب/i.test(bodyText);
                const hasDeal = /(فروش|رهن|اجاره|ودیعه)/i.test(bodyText);
                const hasType = /(آپارتمان|خانه|ملک|ویلایی|ویلا|مسکونی|تجاری)/i.test(bodyText);
                const score = (hasArea ? 3 : 0) + (hasPrice ? 3 : 0) + (hasRooms ? 2 : 0) + (hasDeal ? 1 : 0) + (hasType ? 2 : 0);
                if (score < 5 || (!hasArea && !(hasPrice && hasRooms && hasType))) continue;
                const img = card.querySelector('img');
                const heading = card.querySelector('h1,h2,h3,h4,[class*="title"],[class*="Title"]');
                let title = clean(heading ? heading.innerText : '');
                if (!title) {
                  const lines = bodyText.split(/[\n|•]/).map(clean).filter(Boolean);
                  title = lines.find(x => x.length > 5 && x.length < 140 && !numeric.test(x)) || bodyText.slice(0,140);
                }
                const price = (bodyText.match(/[0-9۰-۹٠-٩][0-9۰-۹٠-٩.,٬،\s]*(?:تومان|میلیارد|میلیون)/i) || [''])[0];
                const imageUrl = img ? (img.currentSrc || img.src || img.getAttribute('data-src') || img.getAttribute('data-lazy-src') || '') : '';
                seen.add(url);
                found.push({title:title.slice(0,180), summary:bodyText.slice(0,850), price:price, image:imageUrl, url:url});
                if (found.length >= 80) break;
              }
              return JSON.stringify({
                url:location.href,
                items:found,
                debug:{title:document.title, anchors:anchorsSeen, domSeeds:seeds.length, candidates:candidatesSeen,
                  body:clean(document.body ? document.body.innerText : '').slice(0,700)}
              });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { value ->
            try {
                val decoded = JSONTokener(value ?: "null").nextValue() as? String
                val result = JSONObject(decoded ?: "{}")
                val items = result.optJSONArray("items") ?: JSONArray()
                if (items.length() == 0) {
                    val debug = result.optJSONObject("debug")
                    val body = debug?.optString("body").orEmpty().take(260)
                    val anchors = debug?.optInt("anchors", 0) ?: 0
                    val seeds = debug?.optInt("domSeeds", 0) ?: 0
                    Toast.makeText(this, "آگهی پیدا نشد؛ لینک‌ها: $anchors، عناصر صفحه: $seeds. متن صفحه: $body", Toast.LENGTH_LONG).show()
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
