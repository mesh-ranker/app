package ru.meshrating.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Вход в МЭШ через WebView.
 *
 * Без EXTRA_START_URL открывает страницу refresh-токена (старый сценарий).
 * С EXTRA_START_URL = https://school.mos.ru открывает обычный сайт МЭШ,
 * перехватывает Authorization из запросов WebView, а также токен из cookies
 * и localStorage. Как только токен найден — окно закрывается автоматически.
 */
class MeshLoginActivity : AppCompatActivity() {

    /** Мост для страницы входа: сюда приходят «сигналы» о токене. */
    private inner class LoginBridge {
        @JavascriptInterface
        fun signal(json: String) {
            val candidate = MeshCore.extractToken(json)
            if (candidate != null) runOnUiThread { finishWithToken(candidate, "страница МЭШ") }
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "MeshLogin"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_START_URL = "start_url"
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
            allowFileAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = MeshCore.USER_AGENT
        }
        webView.addJavascriptInterface(LoginBridge(), "MeshAndroid")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request != null) {
                    try {
                        val authorization = request.requestHeaders.entries
                            .firstOrNull { it.key.equals("Authorization", true) }
                            ?.value
                        if (!authorization.isNullOrBlank()) {
                            runOnUiThread { check(authorization, "Authorization") }
                        }
                        val requestUrl = request.url?.toString().orEmpty()
                        if (requestUrl.contains("token=", true) ||
                            requestUrl.contains("access_token=", true) ||
                            requestUrl.contains("auth_token=", true)
                        ) {
                            runOnUiThread { check(requestUrl, "адрес запроса") }
                        }
                    } catch (_: Exception) {
                        // Некоторые WebView не отдают заголовки запросов — не мешаем загрузке.
                    }
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                checkCookies()
                url?.let { check(it, "навигация") }
                view?.evaluateJavascript(CAPTURE_SCRIPT, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkCookies()
                url?.let { check(it, "навигация") }
                view?.evaluateJavascript(CAPTURE_SCRIPT + "\nMeshAndroid.log('capture: ' + location.href);", null)
                view?.evaluateJavascript("document.body ? document.body.innerText.substring(0, 20000) : ''") { text ->
                    if (text != null) check(text.trim('"').replace("\\n", "\n"), "страница")
                }
            }
        }
        val startUrl = intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.isNotBlank() }
            ?: MeshCore.loginUrl()
        webView.loadUrl(startUrl)

        // Периодическая проверка: cookies, localStorage и адрес.
        scope.launch {
            while (isActive && !finished) {
                delay(1500)
                if (finished) break
                checkCookies()
                webView.url?.let { check(it, "адрес") }
                webView.evaluateJavascript(CAPTURE_SCRIPT, null)
                webView.evaluateJavascript(SCAN_SCRIPT) { result ->
                    if (result != null && result != "null") check(result.trim('"').replace("\\\"", "\""), "localStorage")
                }
            }
        }
    }

    private fun checkCookies() {
        val cookies = CookieManager.getInstance().getCookie(MeshCore.HOST) ?: return
        check(cookies, "cookie")
    }

    private fun check(candidate: String?, source: String) {
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
        webView.removeJavascriptInterface("MeshAndroid")
        super.onDestroy()
    }
}

/** Скрипт-перехватчик: ловит Authorization, токен в localStorage, fetch и XHR. */
private const val CAPTURE_SCRIPT = """
(function () {
  if (window.__meshCaptureInstalled) return;
  window.__meshCaptureInstalled = true;
  function send(value) {
    try { MeshAndroid.signal(String(value)); } catch (error) {}
  }
  function sendAuthorization(headers) {
    if (!headers) return;
    try {
      if (typeof headers.get === 'function') {
        var value = headers.get('Authorization') || headers.get('authorization');
        if (value) send(value);
        return;
      }
      if (Array.isArray(headers)) {
        headers.forEach(function (pair) {
          if (pair && pair.length > 1 && /^authorization$/i.test(String(pair[0]))) send(pair[1]);
        });
        return;
      }
      if (typeof headers === 'object') {
        Object.keys(headers).forEach(function (key) {
          if (/^authorization$/i.test(key)) send(headers[key]);
        });
      }
    } catch (error) {}
  }
  try {
    var originalSet = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      var result = originalSet.apply(this, arguments);
      if (value !== undefined && value !== null) send(value);
      return result;
    };
  } catch (error) {}
  try {
    var originalFetch = window.fetch;
    if (typeof originalFetch === 'function') {
      window.fetch = function () {
        var args = arguments;
        try {
          if (args[0] && args[0].headers) sendAuthorization(args[0].headers);
          if (args[1] && args[1].headers) sendAuthorization(args[1].headers);
        } catch (error) {}
        return originalFetch.apply(this, args).then(function (response) {
          try {
            if (/token|auth|session|login|profile/i.test(response.url || '')) {
              response.clone().text().then(send).catch(function () {});
            }
          } catch (error) {}
          return response;
        });
      };
    }
  } catch (error) {}
  try {
    var originalOpen = XMLHttpRequest.prototype.open;
    var originalSend = XMLHttpRequest.prototype.send;
    var originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.open = function (method, url) { this.__meshUrl = url; return originalOpen.apply(this, arguments); };
    XMLHttpRequest.prototype.setRequestHeader = function (name, value) {
      try { if (/^authorization$/i.test(String(name))) send(value); } catch (error) {}
      return originalSetRequestHeader.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      this.addEventListener('load', function () {
        try {
          if (/token|auth|session|login|profile/i.test(String(this.__meshUrl || ''))) send(this.responseText);
        } catch (error) {}
      });
      return originalSend.apply(this, arguments);
    };
  } catch (error) {}
})();
"""

/** Скрипт-опрос: возвращает значение из localStorage или адрес страницы. */
private const val SCAN_SCRIPT = """
(function () {
  try {
    for (var i = 0; i < localStorage.length; i++) {
      var key = localStorage.key(i);
      var value = localStorage.getItem(key) || '';
      if (/token/i.test(key) || value.length > 24) return value;
    }
  } catch (error) {}
  return location.href.indexOf('token=') >= 0 ? location.href : null;
})();
"""
