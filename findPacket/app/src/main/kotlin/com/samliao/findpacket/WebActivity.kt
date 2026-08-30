package com.samliao.findpacket

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class WebActivity : Activity() {
    private lateinit var webView: WebView
    private var trackingNumber: String = ""
    private var autofillSelector: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        trackingNumber = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        autofillSelector = intent.getStringExtra(EXTRA_AUTOFILL_SELECTOR).orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(LinearLayout(this).apply {
            setPadding(12, 12, 12, 12)

            addView(TextView(this@WebActivity).apply {
                text = title
                textSize = 18f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(Button(this@WebActivity).apply {
                isAllCaps = false
                setText(R.string.open_browser_short)
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            })
        })

        webView = WebView(this).apply {
            settings.domStorageEnabled = true
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    autofillTrackingNumber()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return false
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        if (url.isNotEmpty()) {
            webView.loadUrl(url)
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_AUTOFILL_SELECTOR = "autofill_selector"
    }

    private fun autofillTrackingNumber() {
        if (trackingNumber.isBlank()) return
        val script = """
            (function(number, selector) {
              function ownTextOf(el) {
                if (!el) return '';
                return [
                  el.id, el.name, el.placeholder, el.title, el.getAttribute('aria-label')
                ].filter(Boolean).join(' ').toLowerCase();
              }
              function contextTextOf(el) {
                if (!el) return '';
                return [
                  ownTextOf(el),
                  el.parentElement && el.parentElement.innerText
                ].filter(Boolean).join(' ').toLowerCase();
              }
              function labelTextOf(el) {
                return (el && el.closest('label') && el.closest('label').innerText || '').toLowerCase();
              }
              function badOwn(text) {
                return /驗證|captcha|recaptcha|chk|checkcode|verification|verify|security|站內搜尋|搜尋|姓名|手機|電話|密碼|password|mail|email/.test(text);
              }
              function badLabel(text) {
                return /驗證|captcha|recaptcha|checkcode|verification|verify|security|姓名|手機|電話|密碼|password|mail|email/.test(text);
              }
              function fillInput(input) {
                if (!input) return false;
                input.focus();
                input.value = number;
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
              }
              function fillSelector(doc) {
                if (!selector) return false;
                try {
                  return fillInput(doc.querySelector(selector));
                } catch (e) {
                  return false;
                }
              }
              function score(input) {
                var type = (input.type || '').toLowerCase();
                if (input.disabled || input.readOnly || /hidden|button|submit|reset|checkbox|radio|password/.test(type)) return -1;
                var ownText = ownTextOf(input);
                var contextText = contextTextOf(input);
                if (badOwn(ownText) || badLabel(labelTextOf(input))) return -1;
                if ((input.maxLength || 0) > 0 && input.maxLength <= 6 && !/單號|貨號|提單|tracking|waybill/.test(ownText)) return -1;
                var s = 0;
                if (/單號|貨號|提單|取貨|寄件/.test(ownText)) s += 20;
                if (/track|tracking|trace|shipment|package|parcel|waybill|barcode|order/.test(ownText)) s += 16;
                if (/編號|號碼|包裹|物流|宅配|貨件/.test(contextText)) s += 5;
                if (/number|no|num|id/.test(ownText)) s += 3;
                if (!input.value) s += 1;
                return s;
              }
              function fill(doc) {
                if (fillSelector(doc)) return true;
                var inputs = Array.prototype.slice.call(doc.querySelectorAll('input, textarea'));
                var best = inputs.map(function(input) {
                  return { input: input, score: score(input) };
                }).sort(function(a, b) { return b.score - a.score; })[0];
                if (!best || best.score < 1) return false;
                return fillInput(best.input);
              }
              var ok = fill(document);
              Array.prototype.forEach.call(window.frames, function(frame) {
                try { ok = fill(frame.document) || ok; } catch (e) {}
              });
              return ok;
            })(${JSONObject.quote(trackingNumber)}, ${JSONObject.quote(autofillSelector)});
        """.trimIndent()
        webView.evaluateJavascript(script, null)
        webView.postDelayed({ webView.evaluateJavascript(script, null) }, 800)
        webView.postDelayed({ webView.evaluateJavascript(script, null) }, 2000)
    }
}
