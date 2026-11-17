package md.sime.agenda.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import md.sime.agenda.model.LessonDetail
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.model.UserType
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

sealed class BridgeResult {
    data class Success(
        val mainPageUrl: String,
        val mainPageHtml: String,
        val reusitaHtml: String? = null,
        val dosarHtml: String? = null,
        val absenteHtml: String? = null,
        val mesajeHtml: String? = null,
        val calendarEvents: List<LessonEvent> = emptyList()
    ) : BridgeResult()

    data class Failure(
        val message: String,
        val isAuthFailure: Boolean = false
    ) : BridgeResult()
}

class SimeSessionBridge(private val context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val capturedCalendarJsonBatches = ConcurrentLinkedQueue<String>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val cm = CookieManager.getInstance()
            val urlStr = url.toString()
            cookies.forEach { cookie -> cm.setCookie(urlStr, cookie.toString()) }
            cm.flush()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
            return raw.split(";")
                .mapNotNull { pair ->
                    val trimmed = pair.trim()
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx <= 0) return@mapNotNull null
                    val name = trimmed.substring(0, eqIdx).trim()
                    val value = trimmed.substring(eqIdx + 1).trim()
                    Cookie.Builder()
                        .domain(url.host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build()
                }
        }
    }

    private val okHttp = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @SuppressLint("SetJavaScriptEnabled")
    private fun createOrResetWebView(onUrlReached: (String) -> Unit): WebView {
        CookieManager.getInstance().setAcceptCookie(true)
        val wv = WebView(appContext)
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(4200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(3200, View.MeasureSpec.EXACTLY)
        )
        wv.layout(0, 0, 4200, 3200)
        wv.setInitialScale(100)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = false
            blockNetworkImage = true
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        }
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onCalendarBatch(jsonText: String) {
                capturedCalendarJsonBatches.add(jsonText)
            }
        }, "SimeBridge")

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!url.isNullOrBlank()) {
                    onUrlReached(url)
                }
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                if (view != null && url != null && url.contains("/main-page")) {
                    injectXhrHook(view)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (view != null && url != null) {
                    if (url.contains("/main-page")) {
                        injectXhrHook(view)
                    }
                    onUrlReached(url)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
            }
        }
        return wv
    }

    private fun injectXhrHook(view: WebView) {
        val hookJs = """
            (function() {
              if (window.__simeHooked) return;
              window.__simeHooked = true;
              window.__simeCalendarBatches = [];
              function recordIfCalendar(url, txt) {
                try {
                  if (url && url.indexOf('wwv_flow.ajax') !== -1 && txt) {
                    var t = txt.trim();
                    if (t.charAt(0) === '[' && t.indexOf('lesson-card') !== -1) {
                      window.__simeCalendarBatches.push(t);
                      if (window.SimeBridge && window.SimeBridge.onCalendarBatch) {
                        window.SimeBridge.onCalendarBatch(t);
                      }
                    }
                  }
                } catch (e) {}
              }
              var origOpen = XMLHttpRequest.prototype.open;
              var origSend = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function(method, url) {
                this.__simeUrl = url || '';
                return origOpen.apply(this, arguments);
              };
              XMLHttpRequest.prototype.send = function() {
                this.addEventListener('load', function() {
                  recordIfCalendar(this.__simeUrl, this.responseText);
                });
                return origSend.apply(this, arguments);
              };
              if (window.fetch) {
                var origFetch = window.fetch;
                window.fetch = function(input, init) {
                  var reqUrl = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                  return origFetch.apply(this, arguments).then(function(resp) {
                    try {
                      if (reqUrl.indexOf('wwv_flow.ajax') !== -1) {
                        resp.clone().text().then(function(txt) {
                          recordIfCalendar(reqUrl, txt);
                        });
                      }
                    } catch (e) {}
                    return resp;
                  });
                };
              }
            })();
        """.trimIndent()
        view.evaluateJavascript(hookJs, null)
    }

    suspend fun fetchFastWithExistingSession(lastMainUrl: String?): BridgeResult? = withContext(Dispatchers.IO) {
        if (lastMainUrl.isNullOrBlank()) return@withContext null
        runCatching {
            val req = Request.Builder().url(lastMainUrl).get().build()
            okHttp.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful && finalUrl.contains("/main-page") && body.contains("homework_list")) {
                    BridgeResult.Success(
                        mainPageUrl = finalUrl,
                        mainPageHtml = body,
                        calendarEvents = emptyList()
                    )
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    suspend fun loginAndFetchPortal(
        username: String,
        password: String,
        userType: UserType,
        prefetchSemesterMonths: Boolean = true,
        targetMainUrl: String? = null
    ): BridgeResult = mutex.withLock {
        capturedCalendarJsonBatches.clear()
        val webViewHolder = arrayOfNulls<WebView>(1)

        try {
            val initFailure = openAuthenticatedMainPage(
                webViewHolder = webViewHolder,
                username = username,
                password = password,
                userType = userType,
                targetMainUrl = targetMainUrl
            )
            if (initFailure != null) return@withLock initFailure
            val wv = webViewHolder[0]
                ?: return@withLock BridgeResult.Failure("Nu s-a putut inițializa motorul WebView.")

            // Step 3: Wait for main-page DOM (Dashboard + hidden P1_* items)
            waitForCondition(10000L) {
                val ready = evalJsString(wv, "document.readyState")
                ready == "complete" && hasDashboardContent(wv)
            }
            val baseHtml = evalJsString(wv, "document.documentElement.outerHTML")

            // Step 4: Activate each zero-network APEX badge in turn to capture lazy-populated DOM regions
            val reusitaHtml = captureBadgeSectionHtml(
                wv = wv,
                badgeSelector = ".badge-reusita",
                containerSelector = "#reusita-container",
                waitReadySelector = "#report_table_sem1 tbody tr"
            )
            val dosarHtml = captureBadgeSectionHtml(
                wv = wv,
                badgeSelector = ".badge-dosar",
                containerSelector = "#dosarRegion",
                waitReadySelector = "#R546889360489434893 tbody tr"
            )
            val absenteHtml = captureAbsencesSectionHtml(wv)
            val mesajeHtml = captureBadgeSectionHtml(
                wv = wv,
                badgeSelector = ".badge-mesaje",
                containerSelector = "#R25686519109815893",
                waitReadySelector = "#messageContainer"
            )

            // Step 5: Open Agenda tile and capture schedule across semester months
            activateAgendaInMonthView(wv)

            if (prefetchSemesterMonths || capturedCalendarJsonBatches.isEmpty()) {
                prefetchSemesterCalendarBatches(wv)
            } else {
                captureVisibleDomMonthEvents(wv)
            }

            val finalUrl = withContext(Dispatchers.Main) { wv.url ?: MAIN_BASE_URL }
            val html = if (baseHtml.contains("RegBadge")) baseHtml
            else evalJsString(wv, "document.documentElement.outerHTML")
            if (html.isBlank() || !html.contains("RegBadge")) {
                return@withLock BridgeResult.Failure("Nu s-au putut încărca datele de pe pagina principală SIME.")
            }

            collectJsBatchesIntoQueue(wv)
            val allEvents = parseAndMergeAllBatches()
            CookieManager.getInstance().flush()

            BridgeResult.Success(
                mainPageUrl = finalUrl,
                mainPageHtml = html,
                reusitaHtml = reusitaHtml,
                dosarHtml = dosarHtml,
                absenteHtml = absenteHtml,
                mesajeHtml = mesajeHtml,
                calendarEvents = allEvents
            )
        } catch (t: Throwable) {
            BridgeResult.Failure("Eroare la sincronizare: ${t.message ?: "necunoscută"}")
        } finally {
            destroyWebView(webViewHolder)
        }
    }

    suspend fun fetchCalendarMonthForDate(
        targetDateIso: String,
        username: String,
        password: String,
        userType: UserType,
        targetMainUrl: String?
    ): Result<Pair<String, List<LessonEvent>>> = mutex.withLock {
        capturedCalendarJsonBatches.clear()
        val webViewHolder = arrayOfNulls<WebView>(1)

        try {
            val initFailure = openAuthenticatedMainPage(
                webViewHolder = webViewHolder,
                username = username,
                password = password,
                userType = userType,
                targetMainUrl = targetMainUrl
            )
            if (initFailure != null) return@withLock Result.failure(Exception(initFailure.message))
            val wv = webViewHolder[0]
                ?: return@withLock Result.failure(Exception("WebView indisponibil."))

            waitForCondition(10000L) {
                evalJsString(wv, "document.readyState") == "complete" && hasDashboardContent(wv)
            }
            activateAgendaInMonthView(wv)
            captureVisibleDomMonthEvents(wv)

            val targetDate = runCatching { LocalDate.parse(targetDateIso) }.getOrElse { LocalDate.now() }
            val today = LocalDate.now()
            val monthsDelta = (targetDate.year - today.year) * 12 + (targetDate.monthValue - today.monthValue)
            val steps = kotlin.math.abs(monthsDelta).coerceIn(0, 10)
            val btnSelector = if (monthsDelta < 0) {
                ".fc-prev-button, #orar_calendar button[title*='Prev'], #orar_calendar button[aria-label*='Prev']"
            } else {
                ".fc-next-button, #orar_calendar button[title*='Next'], #orar_calendar button[aria-label*='Next']"
            }

            for (step in 1..steps) {
                stepCalendarAndCapture(wv, btnSelector)
            }

            val finalUrl = withContext(Dispatchers.Main) { wv.url ?: MAIN_BASE_URL }
            collectJsBatchesIntoQueue(wv)
            val events = parseAndMergeAllBatches()
            CookieManager.getInstance().flush()
            Result.success(finalUrl to events)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            destroyWebView(webViewHolder)
        }
    }

    private suspend fun openAuthenticatedMainPage(
        webViewHolder: Array<WebView?>,
        username: String,
        password: String,
        userType: UserType,
        targetMainUrl: String?
    ): BridgeResult.Failure? {
        var currentUrl = ""
        withContext(Dispatchers.Main) {
            webViewHolder[0] = createOrResetWebView { reached ->
                currentUrl = reached
            }
        }
        val wv = webViewHolder[0]
            ?: return BridgeResult.Failure("Nu s-a putut inițializa motorul WebView.")

        val initialUrl = targetMainUrl ?: PUBLIC_URL
        withContext(Dispatchers.Main) { wv.loadUrl(initialUrl) }

        var landed = waitForCondition(6000L) {
            val href = evalJsString(wv, "window.location.href")
            val ready = evalJsString(wv, "document.readyState")
            ready == "complete" && (href.contains("/main-page") || href.contains("/public"))
        }
        if (!landed && targetMainUrl != null) {
            withContext(Dispatchers.Main) { wv.loadUrl(PUBLIC_URL) }
            landed = waitForCondition(8000L) {
                val href = evalJsString(wv, "window.location.href")
                val ready = evalJsString(wv, "document.readyState")
                ready == "complete" && (href.contains("/main-page") || href.contains("/public"))
            }
        }
        if (!landed) {
            return BridgeResult.Failure(
                "Conexiunea la agenda.sime.md a expirat. Verifică internetul și încearcă din nou."
            )
        }

        val curHref = evalJsString(wv, "window.location.href")
        if (!curHref.contains("/main-page") || !hasDashboardContent(wv)) {
            val loginResult = performApexLogin(wv, username, password, userType) { currentUrl }
            if (loginResult != null) return loginResult
        }
        return null
    }

    private suspend fun destroyWebView(webViewHolder: Array<WebView?>) {
        withContext(Dispatchers.Main) {
            webViewHolder[0]?.apply {
                stopLoading()
                destroy()
            }
            webViewHolder[0] = null
        }
    }

    private suspend fun collectJsBatchesIntoQueue(wv: WebView) {
        val extraBatchesJson = evalJsString(
            wv,
            "JSON.stringify(window.__simeCalendarBatches || [])"
        )
        runCatching {
            val arr = org.json.JSONArray(extraBatchesJson)
            for (i in 0 until arr.length()) {
                capturedCalendarJsonBatches.add(arr.getString(i))
            }
        }
    }

    private suspend fun activateAgendaInMonthView(wv: WebView) {
        withContext(Dispatchers.Main) {
            injectXhrHook(wv)
            wv.evaluateJavascript(
                """
                (function() {
                  var b = document.querySelector('.badge-agenda');
                  if (b) b.click();
                })();
                """.trimIndent(),
                null
            )
        }
        delay(1200)
        withContext(Dispatchers.Main) {
            wv.evaluateJavascript(
                """
                (function() {
                  var monthBtn = document.querySelector('.fc-dayGridMonth-button');
                  if (!monthBtn) {
                    var btns = document.querySelectorAll('#orar_calendar button');
                    for (var i = 0; i < btns.length; i++) {
                      if ((btns[i].innerText || '').trim().toLowerCase().indexOf('lun') === 0) {
                        monthBtn = btns[i];
                        break;
                      }
                    }
                  }
                  if (monthBtn) monthBtn.click();
                })();
                """.trimIndent(),
                null
            )
        }
        waitForCondition(2500L) {
            evalJsString(
                wv,
                "document.querySelectorAll('#orar_calendar [data-date] .fc-event').length > 0 ? 'YES' : 'NO'"
            ) == "YES"
        }
        delay(300)
    }

    private suspend fun stepCalendarAndCapture(wv: WebView, btnSelector: String): String {
        val prevCount = capturedCalendarJsonBatches.size
        val prevTitle = evalJsString(
            wv,
            "(function(){var t=document.querySelector('.fc-toolbar-title');return t?t.innerText:'';})();"
        )
        val escapedSel = JSONObject.quote(btnSelector)
        val titleText = evalJsString(
            wv,
            """
            (function() {
              var btn = document.querySelector($escapedSel);
              if (btn) btn.click();
              var t = document.querySelector('.fc-toolbar-title');
              return t ? t.innerText : '';
            })();
            """.trimIndent()
        )
        waitForCondition(2500L) {
            capturedCalendarJsonBatches.size > prevCount ||
                evalJsString(wv, "(function(){var t=document.querySelector('.fc-toolbar-title');return t?t.innerText:'';})();") != prevTitle
        }
        delay(350)
        captureVisibleDomMonthEvents(wv)
        return titleText
    }

    private suspend fun performApexLogin(
        wv: WebView,
        username: String,
        password: String,
        userType: UserType,
        currentUrlProvider: () -> String
    ): BridgeResult.Failure? {
        // Click Elev (2) or Părinte (1) login button
        val openModalJs = """
            (function() {
              var link = document.querySelector('a[href*="p101_user_type=${userType.apexCode}"]');
              if (link) { link.click(); return 'CLICKED'; }
              return 'NOT_FOUND';
            })();
        """.trimIndent()
        val clicked = evalJsString(wv, openModalJs)
        if (clicked != "CLICKED") {
            return BridgeResult.Failure("Nu s-a găsit butonul de autentificare pe agenda.sime.md.")
        }

        // Wait for login_desktop iframe to load
        val iframeReady = waitForCondition(10000L) {
            val state = evalJsString(
                wv,
                """
                (function() {
                  var f = document.querySelector('.ui-dialog iframe, iframe[src*="login_desktop"]');
                  if (!f || !f.contentDocument) return 'NO_IFRAME';
                  var u = f.contentDocument.getElementById('P101_USERNAME');
                  var p = f.contentDocument.getElementById('P101_PASSWORD');
                  var b = f.contentDocument.getElementById('LOGIN');
                  return (u && p && b) ? 'READY' : 'LOADING';
                })();
                """.trimIndent()
            )
            state == "READY"
        }
        if (!iframeReady) {
            return BridgeResult.Failure("Fereastra de autentificare nu s-a încărcat la timp.")
        }

        val escapedUser = JSONObject.quote(username)
        val escapedPass = JSONObject.quote(password)
        val submitJs = """
            (function() {
              var f = document.querySelector('.ui-dialog iframe, iframe[src*="login_desktop"]');
              var doc = f.contentDocument;
              var u = doc.getElementById('P101_USERNAME');
              var p = doc.getElementById('P101_PASSWORD');
              var b = doc.getElementById('LOGIN');
              u.value = $escapedUser;
              u.dispatchEvent(new Event('input', { bubbles: true }));
              u.dispatchEvent(new Event('change', { bubbles: true }));
              p.value = $escapedPass;
              p.dispatchEvent(new Event('input', { bubbles: true }));
              p.dispatchEvent(new Event('change', { bubbles: true }));
              b.click();
              return 'SUBMITTED';
            })();
        """.trimIndent()
        evalJsString(wv, submitJs)

        // Wait for either main-page navigation OR an inline APEX error inside the iframe
        var authError: String? = null
        val loggedIn = waitForCondition(15000L) {
            val onMain = evalJsString(
                wv,
                "(window.location.href.indexOf('/main-page') !== -1 && document.getElementById('RegBadge')) ? 'YES' : 'NO'"
            ) == "YES"
            if (onMain || (currentUrlProvider().contains("/main-page") && hasDashboardContent(wv))) {
                return@waitForCondition true
            }
            val err = evalJsString(
                wv,
                """
                (function() {
                  var f = document.querySelector('.ui-dialog iframe, iframe[src*="login_desktop"]');
                  if (!f || !f.contentDocument) return '';
                  var el = f.contentDocument.querySelector('.t-Alert-body, .a-Notification-item, .u-Processing--error, #APEX_ERROR_MESSAGE');
                  return el ? el.innerText.trim() : '';
                })();
                """.trimIndent()
            )
            if (err.isNotBlank()) {
                authError = err
                return@waitForCondition true
            }
            false
        }

        if (!authError.isNullOrBlank()) {
            return BridgeResult.Failure(authError!!, isAuthFailure = true)
        }
        val finalOnMain = currentUrlProvider().contains("/main-page") ||
            evalJsString(wv, "window.location.href").contains("/main-page")
        if (!loggedIn || !finalOnMain) {
            return BridgeResult.Failure(
                "Autentificarea a eșuat. Verifică numele de utilizator și parola.",
                isAuthFailure = true
            )
        }
        return null
    }

    private suspend fun captureBadgeSectionHtml(
        wv: WebView,
        badgeSelector: String,
        containerSelector: String,
        waitReadySelector: String
    ): String {
        withContext(Dispatchers.Main) {
            wv.evaluateJavascript(
                "(function(){var b=document.querySelector('$badgeSelector');if(b)b.click();})();",
                null
            )
        }
        waitForCondition(1500L) {
            evalJsString(
                wv,
                "document.querySelectorAll('$waitReadySelector').length > 0 ? 'YES' : 'NO'"
            ) == "YES"
        }
        delay(120)
        return evalJsString(
            wv,
            "(function(){var c=document.querySelector('$containerSelector');return c?c.outerHTML:'';})();"
        )
    }

    private suspend fun captureAbsencesSectionHtml(wv: WebView): String {
        withContext(Dispatchers.Main) {
            wv.evaluateJavascript(
                "(function(){var b=document.querySelector('.badge-absente');if(b)b.click();})();",
                null
            )
        }
        waitForCondition(1500L) {
            evalJsString(
                wv,
                "document.querySelectorAll('.absence-report-container .month-header').length > 0 ? 'YES' : 'NO'"
            ) == "YES"
        }
        delay(120)
        // Expand all 9 month cards so toggleCard() populates every .absence-field row into the DOM
        evalJsString(
            wv,
            """
            (function() {
              var headers = document.querySelectorAll('.absence-report-container .month-header');
              for (var i = 0; i < headers.length; i++) {
                headers[i].click();
              }
              return String(headers.length);
            })();
            """.trimIndent()
        )
        delay(220)
        val zilnicaHtml = evalJsString(
            wv,
            "(function(){var c=document.querySelector('#custom-tabs-blue')||document.querySelector('#R988934330764996473');return c?c.outerHTML:'';})();"
        )
        // Also switch to Discipline școlare tab to ensure per-subject absence table is populated
        evalJsString(
            wv,
            """
            (function() {
              var tabs = document.querySelectorAll('#custom-tabs-blue [role="tab"]');
              if (tabs.length > 1) tabs[1].click();
              return 'OK';
            })();
            """.trimIndent()
        )
        delay(200)
        val combinedHtml = evalJsString(
            wv,
            "(function(){var c=document.querySelector('#custom-tabs-blue')||document.querySelector('#R988934330764996473');return c?c.outerHTML:'';})();"
        )
        return if (combinedHtml.contains("absence-field")) combinedHtml else "$zilnicaHtml\n$combinedHtml"
    }

    private suspend fun prefetchSemesterCalendarBatches(wv: WebView) {
        val isMonthGrid = evalJsString(
            wv,
            "document.querySelectorAll('#orar_calendar .fc-daygrid-day[data-date]').length > 0 ? 'YES' : 'NO'"
        ) == "YES"
        val debugInfo = evalJsString(
            wv,
            "(function(){var t=document.querySelector('.fc-toolbar-title');return 'w=' + window.innerWidth + ' title=' + (t?t.innerText:'') + ' dg=' + document.querySelectorAll('.fc-daygrid-day').length + ' ld=' + document.querySelectorAll('.fc-list-day').length;})();"
        )
        Log.d("SimeBridge", "Calendar initial state: $debugInfo")

        captureVisibleDomMonthEvents(wv)

        val isWeekView = debugInfo.contains("–") || debugInfo.contains("-")
        val prevSteps = when {
            isMonthGrid -> 3
            isWeekView -> 12
            else -> 12
        }
        for (step in 1..prevSteps) {
            val titleText = stepCalendarAndCapture(
                wv,
                ".fc-prev-button, #orar_calendar button[title*='Prev'], #orar_calendar button[aria-label*='Prev']"
            )
            Log.d("SimeBridge", "Step $step title=$titleText batches=${capturedCalendarJsonBatches.size}")
            if (titleText.contains("aug", ignoreCase = true) || titleText.contains("iun", ignoreCase = true)) {
                break
            }
        }

        // Jump back to Today ("Azi") and step Next twice so upcoming months/weeks are included too
        stepCalendarAndCapture(wv, ".fc-today-button")
        val nextSteps = if (isMonthGrid) 2 else 3
        for (step in 1..nextSteps) {
            stepCalendarAndCapture(
                wv,
                ".fc-next-button, #orar_calendar button[title*='Next'], #orar_calendar button[aria-label*='Next']"
            )
        }
    }

    private suspend fun captureVisibleDomMonthEvents(wv: WebView) {
        val domJson = evalJsString(
            wv,
            """
            (function() {
              var out = [];
              var cells = document.querySelectorAll('#orar_calendar .fc-daygrid-day[data-date], #orar_calendar [data-date]');
              for (var i = 0; i < cells.length; i++) {
                var dateIso = cells[i].getAttribute('data-date');
                if (!dateIso || cells[i].classList.contains('fc-list-day')) continue;
                var evs = cells[i].querySelectorAll('.fc-event');
                for (var j = 0; j < evs.length; j++) {
                  var el = evs[j];
                  var card = el.querySelector('.lesson-card');
                  if (!card) continue;
                  var times = card.querySelectorAll('.lesson-time span');
                  var st = times.length > 0 ? times[0].innerText.trim() : '00:00';
                  var et = times.length > 1 ? times[1].innerText.trim() : '00:00';
                  out.push({
                    start: dateIso + 'T' + st + ':00',
                    end: dateIso + 'T' + et + ':00',
                    title: card.outerHTML,
                    url: el.getAttribute('href') || ''
                  });
                }
              }
              if (out.length === 0) {
                var listDays = document.querySelectorAll('.fc-list-day[data-date]');
                for (var d = 0; d < listDays.length; d++) {
                  var dIso = listDays[d].getAttribute('data-date');
                  var sib = listDays[d].nextElementSibling;
                  while (sib && !sib.classList.contains('fc-list-day')) {
                    var card = sib.querySelector('.lesson-card');
                    if (card) {
                      var times = card.querySelectorAll('.lesson-time span');
                      var st = times.length > 0 ? times[0].innerText.trim() : '00:00';
                      var et = times.length > 1 ? times[1].innerText.trim() : '00:00';
                      var link = sib.querySelector('a[href]');
                      out.push({
                        start: dIso + 'T' + st + ':00',
                        end: dIso + 'T' + et + ':00',
                        title: card.outerHTML,
                        url: link ? (link.getAttribute('href') || '') : ''
                      });
                    }
                    sib = sib.nextElementSibling;
                  }
                }
              }
              return JSON.stringify(out);
            })();
            """.trimIndent()
        )
        if (domJson.startsWith("[")) {
            capturedCalendarJsonBatches.add(domJson)
        }
    }

    private fun parseAndMergeAllBatches(): List<LessonEvent> {
        val merged = LinkedHashMap<String, LessonEvent>()
        while (true) {
            val batch = capturedCalendarJsonBatches.poll() ?: break
            runCatching {
                SimeHtmlParser.parseCalendarEventsJson(batch).forEach { ev ->
                    merged[ev.id] = ev
                }
            }
        }
        return merged.values.sortedWith(compareBy<LessonEvent> { it.dateIso }.thenBy { it.periodNumber })
    }

    suspend fun fetchLessonDetail(detailUrl: String): Result<LessonDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(detailUrl)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || finalUrl.contains("/public") || body.contains("P101_USERNAME")) {
                    throw SessionExpiredException("Sesiunea SIME a expirat.")
                }
                SimeHtmlParser.parseLessonDetailPage(body)
            }
        }
    }

    suspend fun logoutSession(sessionId: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (sessionId.isNotBlank()) {
                val url = "https://agenda.sime.md/ords/r/agenda/main/apex_authentication.logout?p_app_id=104&p_session_id=$sessionId"
                okHttp.newCall(Request.Builder().url(url).get().build()).execute().close()
            }
        }
        withContext(Dispatchers.Main) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    private suspend fun hasDashboardContent(wv: WebView): Boolean {
        val res = evalJsString(
            wv,
            "document.getElementById('RegBadge') ? 'YES' : 'NO'"
        )
        return res == "YES"
    }

    private suspend fun waitForCondition(timeoutMs: Long, predicate: suspend () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            delay(200)
        }
        return false
    }

    private suspend fun evalJsString(wv: WebView, script: String): String {
        val deferred = CompletableDeferred<String>()
        mainHandler.post {
            wv.evaluateJavascript(script) { raw ->
                deferred.complete(decodeJsString(raw))
            }
        }
        return withTimeoutOrNull(4000L) { deferred.await() }.orEmpty()
    }

    private fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return runCatching {
            if (raw.startsWith("\"")) {
                JSONObject("{\"v\":$raw}").getString("v")
            } else {
                raw
            }
        }.getOrDefault(raw)
    }

    class SessionExpiredException(message: String) : Exception(message)

    companion object {
        const val PUBLIC_URL = "https://agenda.sime.md/ords/r/agenda/main/public"
        const val MAIN_BASE_URL = "https://agenda.sime.md/ords/r/agenda/main/main-page"
    }
}
