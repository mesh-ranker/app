package ru.meshrating.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Нативный порт PoC `mos_qr_login.py`: вход в МЭШ по QR-ссылке.
 *
 * 1. GET login.mos.ru/sps/oauth/ae — сервер ставит cookie `fm` (QR-сессия).
 * 2. Раз в секунду GET .../headless/qrCode/pull — получаем ссылку и статусы.
 * 3. Пользователь открывает ссылку и подтверждает вход в приложении МЭШ.
 * 4. POST .../qrCode/complete -> редиректы (код из письма / askToTrust) ->
 *    school.mos.ru выставляет cookie `aupd_token` — это и есть Bearer-токен.
 */
private const val LOGIN = "https://login.mos.ru"
private const val SCHOOL = "https://school.mos.ru"
private const val REDIRECT_URI = "$SCHOOL/v3/auth/sudir/callback"
private const val CLIENT_ID = "dnevnik.mos.ru"
private const val SCOPE =
    "openid profile birthday contacts snils blitz_user_rights blitz_change_password offline_access"
private const val UA =
    "Mozilla/5.0 (X11; Linux x86_64; rv:159.0) Gecko/20100101 Firefox/159.0"

private const val HTML_ACCEPT =
    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
private const val JSON_ACCEPT = "application/json, text/javascript, */*; q=0.01"

private class QrException(message: String) : Exception(message)

private class HttpResponse(
    val url: String,
    val status: Int,
    val location: String?,
    val body: String,
    val contentType: String?
)

private class QrLoginClient {
    // Cookie jar: имена уникальны в рамках mos.ru, поэтому храним по имени.
    private val cookies = LinkedHashMap<String, String>()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun authUrl(): String = LOGIN + "/sps/oauth/ae?" + listOf(
        "response_type" to "code",
        "access_type" to "offline",
        "client_id" to CLIENT_ID,
        "scope" to SCOPE,
        "redirect_uri" to REDIRECT_URI
    ).joinToString("&") { "${enc(it.first)}=${enc(it.second)}" }

    private fun cookieHeader(): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun storeCookies(connection: HttpURLConnection) {
        val fields = runCatching { connection.headerFields }.getOrNull() ?: return
        for ((key, values) in fields) {
            if (key == null || !key.equals("Set-Cookie", ignoreCase = true)) continue
            for (raw in values) {
                val pair = raw.substringBefore(';').trim()
                val idx = pair.indexOf('=')
                if (idx <= 0) continue
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) cookies[name] = value
            }
        }
    }

    private fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            useCaches = false
            runCatching { setRequestProperty("User-Agent", UA) }
            runCatching { setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7") }
            runCatching { setRequestProperty("Sec-GPC", "1") }
            if (cookies.isNotEmpty()) runCatching { setRequestProperty("Cookie", cookieHeader()) }
        }
        for ((key, value) in headers) {
            runCatching { connection.setRequestProperty(key, value) }
        }
        if (form != null) {
            val body = form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
            val bytes = body.toByteArray(Charsets.UTF_8)
            connection.doOutput = true
            runCatching { connection.setFixedLengthStreamingMode(bytes.size) }
            runCatching { connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded") }
            connection.outputStream.use { it.write(bytes) }
        }
        val status = connection.responseCode
        storeCookies(connection)
        val location = connection.getHeaderField("Location")
        val contentType = connection.contentType
        val stream = if (status in 200..299) {
            runCatching { connection.inputStream }.getOrNull()
        } else {
            runCatching { connection.errorStream }.getOrNull()
        }
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        return HttpResponse(url, status, location, body, contentType)
    }

    private fun resolve(base: String, location: String): String = try {
        URL(URL(base), location).toString()
    } catch (_: Exception) {
        location
    }

    private fun isRedirect(response: HttpResponse): Boolean =
        response.status in 300..399 && !response.location.isNullOrBlank()

    private fun openAuth(): String {
        var response = request(
            "GET", authUrl(),
            headers = mapOf(
                "Accept" to HTML_ACCEPT,
                "Referer" to "$SCHOOL/",
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "same-site"
            )
        )
        var hops = 0
        while (isRedirect(response) && hops < 10) {
            val next = resolve(response.url, response.location!!)
            hops++
            if (next.startsWith(SCHOOL)) return next
            response = request(
                "GET", next,
                headers = mapOf(
                    "Accept" to HTML_ACCEPT,
                    "Sec-Fetch-Dest" to "document",
                    "Sec-Fetch-Mode" to "navigate",
                    "Sec-Fetch-Site" to "same-site"
                )
            )
        }
        return response.url
    }

    private fun getPage(url: String, referer: String?): HttpResponse = request(
        "GET", url,
        headers = mapOf(
            "Accept" to HTML_ACCEPT,
            "Referer" to (referer ?: url),
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to if (url.startsWith(SCHOOL)) "cross-site" else "same-site",
            "Upgrade-Insecure-Requests" to "1"
        )
    )

    private fun postPage(url: String, data: Map<String, String>, referer: String): HttpResponse =
        request(
            "POST", url, form = data,
            headers = mapOf(
                "Accept" to HTML_ACCEPT,
                "Referer" to referer,
                "Origin" to LOGIN,
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "same-origin",
                "Upgrade-Insecure-Requests" to "1"
            )
        )

    private fun pull(pageUrl: String): JSONObject? {
        val url = "$LOGIN/sps/login/methods/headless/qrCode/pull?_=${System.currentTimeMillis()}"
        val response = request(
            "GET", url,
            headers = mapOf(
                "Accept" to JSON_ACCEPT,
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to pageUrl
            )
        )
        return runCatching { JSONObject(response.body) }.getOrNull()
    }

    private fun refreshQr(pageUrl: String): JSONObject? {
        val response = request(
            "POST", "$LOGIN/sps/login/methods/headless/qrCode/refresh",
            form = emptyMap(),
            headers = mapOf(
                "Accept" to JSON_ACCEPT,
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to pageUrl,
                "Origin" to LOGIN
            )
        )
        return runCatching { JSONObject(response.body) }.getOrNull()
    }

    private fun completeQr(pageUrl: String): HttpResponse = request(
        "POST", "$LOGIN/sps/login/methods/qrCode/complete",
        form = emptyMap(),
        headers = mapOf(
            "Accept" to HTML_ACCEPT,
            "Referer" to pageUrl,
            "Origin" to LOGIN,
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "same-origin",
            "Upgrade-Insecure-Requests" to "1"
        )
    )

    private fun htmlUnescape(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private fun formAction(html: String, formId: String, fallback: String): String {
        val formRegex = Regex(
            "<form\\b[^>]*\\bid=\"${Regex.escape(formId)}\"[^>]*>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val tag = formRegex.find(html)?.value ?: html
        val action = Regex(
            "\\baction=\"([^\"]*)\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(tag)?.groupValues?.getOrNull(1)
        return if (!action.isNullOrBlank()) resolve(LOGIN, htmlUnescape(action)) else fallback
    }

    private fun hasToken(): Boolean = !cookies["aupd_token"].isNullOrBlank()

    suspend fun run(
        timeoutMs: Long,
        onLink: (String) -> Unit,
        onStatus: (String) -> Unit,
        onEmailCodeRequired: suspend () -> String?,
        onToken: (String) -> Unit
    ) {
        val page = withContext(Dispatchers.IO) { openAuth() }
        if (page.startsWith(SCHOOL)) {
            withContext(Dispatchers.IO) { follow(page) }
        } else {
            val pageUrl = page.ifBlank { "$LOGIN/sps/login/methods/password" }
            val confirmed = poll(pageUrl, timeoutMs, onLink, onStatus)
            if (!confirmed) throw QrException("Время ожидания подтверждения истекло")
            finish(pageUrl, onStatus, onEmailCodeRequired)
        }
        val token = cookies["aupd_token"]
        if (token.isNullOrBlank()) {
            throw QrException("МЭШ не выдал токен. Возможно, нужен код из письма или подтверждение доверия браузеру.")
        }
        currentCoroutineContext().ensureActive()
        onToken(token)
    }

    private fun follow(url: String) {
        var response = getPage(url, null)
        var hops = 0
        while (isRedirect(response) && hops < 25) {
            val next = resolve(response.url, response.location!!)
            hops++
            response = getPage(next, response.url)
        }
    }

    private suspend fun poll(
        pageUrl: String,
        timeoutMs: Long,
        onLink: (String) -> Unit,
        onStatus: (String) -> Unit
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastCommand = ""
        var lastLink = ""
        var lastRefresh = 0L
        var failures = 0
        var unexpected = 0
        while (System.currentTimeMillis() < deadline) {
            val started = System.currentTimeMillis()
            val data = withContext(Dispatchers.IO) { runCatching { pull(pageUrl) }.getOrNull() }
            if (data == null) {
                failures++
                if (failures >= 15 && lastLink.isBlank()) {
                    throw QrException("МЭШ не выдал QR-ссылку. Попробуйте ещё раз.")
                }
            } else {
                failures = 0
            }
            if (data != null) {
                val command = data.optString("command")
                val known = command == "showQRCode" || command == "askForConfirm" ||
                    command == "needComplete" || command == "needRefresh"
                if (!known) {
                    unexpected++
                    if (unexpected >= 5 && lastLink.isBlank()) {
                        val reason = data.optString("inquire").ifBlank { command.ifBlank { "нет command" } }
                        throw QrException("МЭШ не включил QR-вход (ответ: $reason). Попробуйте ещё раз.")
                    }
                } else {
                    unexpected = 0
                }
                when (command) {
                    "showQRCode" -> {
                        val link = data.optString("link")
                        if (link.isNotBlank() && link != lastLink) {
                            lastLink = link
                            onLink(link)
                            onStatus("Откройте ссылку и подтвердите вход в приложении МЭШ.")
                        }
                    }
                    "askForConfirm" -> if (lastCommand != command) {
                        onStatus("Код отсканирован — подтвердите вход в приложении МЭШ.")
                    }
                    "needComplete" -> {
                        onStatus("Подтверждено, завершаем вход...")
                        return true
                    }
                    "needRefresh" -> {
                        if (System.currentTimeMillis() - lastRefresh >= 3_000) {
                            lastRefresh = System.currentTimeMillis()
                            onStatus("Ссылка истекла, запрашиваем новую...")
                            withContext(Dispatchers.IO) { runCatching { refreshQr(pageUrl) } }
                        }
                    }
                }
                if (command.isNotBlank()) lastCommand = command
            }
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 1_000) delay(1_000 - elapsed)
        }
        return false
    }

    private suspend fun finish(
        pageUrl: String,
        onStatus: (String) -> Unit,
        onEmailCodeRequired: suspend () -> String?
    ) {
        var response = withContext(Dispatchers.IO) { completeQr(pageUrl) }
        if (response.contentType?.contains("json", ignoreCase = true) == true) {
            throw QrException("МЭШ ещё не подтвердил вход. Попробуйте ещё раз.")
        }
        var trustDone = false
        for (hop in 0 until 40) {
            if (isRedirect(response)) {
                val next = resolve(response.url, response.location!!)
                response = withContext(Dispatchers.IO) { getPage(next, response.url) }
                continue
            }
            if (hasToken()) return

            val html = response.body
            if (html.contains("verifyDiv") || html.contains("email-code")) {
                onStatus("Нужен код из письма МЭШ")
                val code = onEmailCodeRequired()
                if (code.isNullOrBlank()) throw QrException("Ввод кода отменён")
                val action = formAction(html, "verifyDiv", "$LOGIN/sps/login/methods/email")
                response = withContext(Dispatchers.IO) {
                    postPage(action, mapOf("email-code" to code.trim()), response.url)
                }
                continue
            }
            if (response.url.contains("askToTrust") || html.contains("id=\"decision\"")) {
                if (trustDone) throw QrException("МЭШ не принял подтверждение «доверять браузеру»")
                trustDone = true
                onStatus("Подтверждаем доверие браузеру...")
                val action = formAction(html, "decision", "$LOGIN/sps/login/ur/askToTrust")
                response = withContext(Dispatchers.IO) {
                    postPage(action, mapOf("action" to "trust"), response.url)
                }
                continue
            }
            if (response.url.startsWith(SCHOOL)) {
                throw QrException("school.mos.ru не выдал токен")
            }
            throw QrException("Неожиданная страница МЭШ: ${response.url}")
        }
        if (hasToken()) return
        throw QrException("Не удалось завершить вход по QR")
    }
}

/**
 * UI «Способа 3»: получить ссылку, открыть её, дождаться токена.
 */
@Composable
fun QrLoginPanel(onLogin: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var awaitingEmail by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    val emailDeferred = remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun cancel() {
        job?.cancel()
        job = null
        emailDeferred.value = null
        running = false
        awaitingEmail = false
        status = ""
    }

    fun start() {
        if (running) return
        running = true
        error = null
        link = null
        status = "Готовим QR-сессию..."
        awaitingEmail = false
        emailInput = ""
        val client = QrLoginClient()
        job = scope.launch {
            try {
                client.run(
                    timeoutMs = 300_000,
                    onLink = { link = it },
                    onStatus = { status = it },
                    onEmailCodeRequired = {
                        awaitingEmail = true
                        status = "Введите код из письма МЭШ"
                        val deferred = CompletableDeferred<String?>()
                        emailDeferred.value = deferred
                        val result = deferred.await()
                        awaitingEmail = false
                        emailDeferred.value = null
                        result
                    },
                    onToken = { token ->
                        running = false
                        link = null
                        status = ""
                        emailInput = ""
                        onLogin(token)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Не удалось выполнить QR-вход"
                running = false
                awaitingEmail = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Способ 3. Вход по QR-ссылке",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Нажмите «Получить ссылку», откройте её и подтвердите вход. Приложение получит токен автоматически.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { start() },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (running) "Ожидание подтверждения..." else "Получить ссылку")
        }

        link?.let { value ->
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
                    }
                }
            )
        }

        if (running && status.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (awaitingEmail) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Код из письма") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { emailDeferred.value?.complete(emailInput) },
                    enabled = emailInput.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Подтвердить")
                }
                OutlinedButton(
                    onClick = { emailDeferred.value?.complete(null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отмена")
                }
            }
        }

        if (running) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { cancel() }) { Text("Отменить QR-вход") }
        }

        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}