package com.focusbrowser.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var prefs: SharedPreferences

    // قائمة المواقع المحظورة في LeechBlock
    private val blockedDomains = listOf("tiktok.com", "reddit.com", "netflix.com")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("BrowserPrefs", Context.MODE_PRIVATE)

        // بناء واجهة المستخدم برمجياً لضمان السرعة والخفة
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        urlInput = EditText(this).apply {
            hint = "أدخل الرابط أو ابحث..."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
        }

        val goBtn = Button(this).apply {
            text = "اذهب"
            setOnClickListener { loadUserUrl(urlInput.text.toString()) }
        }

        val extBtn = Button(this).apply {
            text = "الإضافات 🔒"
            setOnClickListener { openExtensionsGate() }
        }

        topBar.addView(urlInput)
        topBar.addView(goBtn)
        topBar.addView(extBtn)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            // تفعيل تسريع الهاردوير لأقصى سلاسة
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    if (isBlockedByLeechBlock(url)) {
                        showBlockedPage(view)
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    urlInput.setText(url)
                    if (url != null && isBlockedByLeechBlock(url)) {
                        showBlockedPage(view)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (prefs.getBoolean("ext_social_focus", true)) {
                        injectSocialFocusCSS(view)
                    }
                }
            }
        }

        rootLayout.addView(topBar)
        rootLayout.addView(webView)
        setContentView(rootLayout)

        webView.loadUrl("https://www.google.com")
    }

    private fun loadUserUrl(input: String) {
        val url = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else if (input.contains(".") && !input.contains(" ")) {
            "https://$input"
        } else {
            "https://www.google.com/search?q=$input"
        }
        webView.loadUrl(url)
    }

    // 1. منطق إضافة LeechBlock NG
    private fun isBlockedByLeechBlock(url: String): Boolean {
        val isLeechBlockEnabled = prefs.getBoolean("ext_leechblock", true)
        if (!isLeechBlockEnabled) return false

        val customBlocked = prefs.getString("custom_blocked", "") ?: ""
        val allBlocked = blockedDomains + customBlocked.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        return allBlocked.any { domain -> url.contains(domain, ignoreCase = true) }
    }

    private fun showBlockedPage(view: WebView?) {
        val html = """
            <html dir="rtl"><body style="background:#121212;color:#fff;text-align:center;padding-top:30%;font-family:sans-serif;">
            <h1 style="color:#ff5252;">تم حظر هذا الموقع بواسطة LeechBlock NG</h1>
            <p>ركز في مهامك الحالية!</p>
            </body></html>
        """.trimIndent()
        view?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    // 2. منطق إضافة Social Focus (إخفاء Shorts, Reels, الخلاصات المشتتة)
    private fun injectSocialFocusCSS(view: WebView?) {
        val css = """
            /* يوتيوب: إخفاء Shorts والتعليقات والفيديوهات المقترحة */
            ytm-reel-shelf-renderer, ytm-pivot-bar-item-renderer:nth-child(2), 
            ytd-reel-shelf-renderer, #related, #comments { display: none !important; }
            /* فيسبوك وإنستغرام: إخفاء Reels والقصص */
            div[aria-label="Reels"], div[data-pagelet="Stories"] { display: none !important; }
        """.trimIndent().replace("\n", " ")

        val js = """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = '$css';
                document.head.appendChild(style);
            })();
        """.trimIndent()

        view?.evaluateJavascript(js, null)
    }

    // 3. بوابة قسم الإضافات (التحقق من الباسورد إذا كان مفعلاً)
    private fun openExtensionsGate() {
        val isPasswordRequired = prefs.getBoolean("ext_lock_enabled", false)
        val savedPassword = prefs.getString("ext_password", "") ?: ""

        if (isPasswordRequired && savedPassword.isNotEmpty()) {
            val passInput = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = "أدخل كلمة المرور"
            }

            AlertDialog.Builder(this)
                .setTitle("قسم الإضافات محمي 🔒")
                .setView(passInput)
                .setPositiveButton("دخول") { _, _ ->
                    if (passInput.text.toString() == savedPassword) {
                        showExtensionsManager()
                    } else {
                        Toast.makeText(this, "كلمة المرور غير صحيحة!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        } else {
            showExtensionsManager()
        }
    }

    // 4. لوحة التحكم في الإضافات وإعدادات كلمة المرور
    private fun showExtensionsManager() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val leechSwitch = Switch(this).apply {
            text = "تفعيل LeechBlock NG (حظر المواقع)"
            isChecked = prefs.getBoolean("ext_leechblock", true)
        }

        val socialSwitch = Switch(this).apply {
            text = "تفعيل Social Focus (إخفاء المشتتات)"
            isChecked = prefs.getBoolean("ext_social_focus", true)
        }

        val lockSwitch = Switch(this).apply {
            text = "حماية قسم الإضافات بكلمة مرور"
            isChecked = prefs.getBoolean("ext_lock_enabled", false)
        }

        val newPassInput = EditText(this).apply {
            hint = "تعيين/تغيير كلمة المرور"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("ext_password", ""))
        }

        val blockedSitesInput = EditText(this).apply {
            hint = "مواقع محظورة إضافية (مفصولة بفاصلة: x.com, fb.com)"
            setText(prefs.getString("custom_blocked", ""))
        }

        layout.addView(leechSwitch)
        layout.addView(blockedSitesInput)
        layout.addView(socialSwitch)
        layout.addView(lockSwitch)
        layout.addView(newPassInput)

        AlertDialog.Builder(this)
            .setTitle("إدارة الإضافات والحماية")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val passText = newPassInput.text.toString()
                if (lockSwitch.isChecked && passText.isEmpty()) {
                    Toast.makeText(this, "يرجى كتابة كلمة مرور لتفعيل القفل", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                prefs.edit().apply {
                    putBoolean("ext_leechblock", leechSwitch.isChecked)
                    putBoolean("ext_social_focus", socialSwitch.isChecked)
                    putBoolean("ext_lock_enabled", lockSwitch.isChecked)
                    putString("ext_password", passText)
                    putString("custom_blocked", blockedSitesInput.text.toString())
                    apply()
                }
                webView.reload()
                Toast.makeText(this, "تم حفظ الإعدادات وتحديث الصفحة", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
