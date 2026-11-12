package ru.meshrating.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLDecoder

/**
 * Второй способ входа: открывает настоящий https://school.mos.ru в WebView.
 * Пользователь входит как обычно, после чего приложение забирает токен из
 * cookie `aupd_token` (или из заголовка Authorization запросов WebView)
 * и само закрывает окно.
 */
class SchoolLoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TOKEN = "token"
        const val EXTRA_SOURCE = "source"
        private const val START_URL = "https://school.mos.ru"
    }

    private lateinit var webView: WebView
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.login_title)
        }

        webView = findViewById(R.id.loginWebView)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = MeshCore.USER_AGENT
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request != null) {
                    try {
                        request.requestHeaders.entries
                            .firstOrNull { it.key.equals("Authorization", true) }
                            ?.value
                            ?.takeIf { it.isNotBlank() }
                            ?.let { authorization ->
                                runOnUiThread { acceptToken(authorization, "Authorization") }
                            }
                    } catch (_: Exception) {
                        // Некоторые версии WebView не отдают заголовки запросов.
                    }
                    val requestUrl = request.url?.toString().orEmpty()
                    if (requestUrl.contains("token=", true) ||
                        requestUrl.contains("access_token=", true) ||
                        requestUrl.contains("auth_token=", true)
                    ) {
                        runOnUiThread { acceptToken(requestUrl, "адрес запроса") }
                    }
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                checkCookies()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkCookies()
            }
        }
        webView.loadUrl(START_URL)

        // Токен появляется в cookie после завершения входа. Проверяем без JS-костылей.
        scope.launch {
            while (isActive && !finished) {
                delay(500)
                if (finished) break
                checkCookies()
                acceptToken(webView.url, "адрес")
            }
        }
    }

    private fun checkCookies() {
        val urls = listOfNotNull(webView.url, START_URL)
            .filter { it.startsWith("http") }
            .distinct()
        for (url in urls) {
            val cookies = CookieManager.getInstance().getCookie(url) ?: continue
            tokenFromCookies(cookies)?.let {
                finishWithToken(it, "cookie")
                return
            }
        }
    }

    private fun tokenFromCookies(cookies: String): String? {
        cookies.split(';').forEach { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@forEach
            val name = part.substring(0, separator).trim()
            if (!name.equals("aupd_token", true) && !name.contains("token", true)) return@forEach
            val value = part.substring(separator + 1)
                .trim()
                .trim('"')
                .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            MeshCore.extractToken(value)?.let { return it }
        }
        return MeshCore.extractToken(cookies)
    }

    private fun acceptToken(candidate: String?, source: String) {
        if (candidate.isNullOrBlank() || finished) return
        val token = MeshCore.extractToken(candidate) ?: return
        finishWithToken(token, source)
    }

    private fun finishWithToken(token: String, source: String) {
        if (finished) return
        finished = true
        Toast.makeText(this, "Токен МЭШ получен", Toast.LENGTH_SHORT).show()
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_TOKEN, token).putExtra(EXTRA_SOURCE, source)
        )
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (webView.canGoBack()) webView.goBack() else finish()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        webView.stopLoading()
        super.onDestroy()
    }
}
