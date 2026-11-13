package ru.meshrating.app

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** Страница питания МЭШ сама загружает баланс и меню в авторизованной web-сессии. */
class FoodActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(this).apply { title = "Еда и баланс"; setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); setNavigationOnClickListener { finish() } }
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = MeshCore.USER_AGENT
            webViewClient = WebViewClient()
            loadUrl("https://school.mos.ru/diary/school/food?food_tab=main")
        }
        root.addView(toolbar, android.widget.LinearLayout.LayoutParams(-1, -2))
        root.addView(webView, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
