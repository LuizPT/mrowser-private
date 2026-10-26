package net.mrowser

import android.text.InputType
import android.view.View
import android.widget.EditText
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import net.mrowser.data.Favorite
import net.mrowser.data.FavoritesJson
import net.mrowser.data.JsonFavoritesStore
import net.mrowser.home.HomeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on an Android emulator; pages are supplied locally and never contact external sites. */
@RunWith(AndroidJUnit4::class)
class PrivateBrowsingInstrumentedTest {

    @Test fun severalWebPagesLeaveNoPersistentVisitList() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val pages = listOf("one", "two", "three").mapIndexed { index, name ->
                LocalPageServer("$name origin", "Private page $index")
            }
            try {
                pages.forEach { loadPage(scenario, it) }
            } finally {
                pages.forEach(Closeable::close)
            }

            var canGoBack = false
            var filesDir: File? = null
            scenario.onActivity { activity ->
                canGoBack = activity.findViewById<WebView>(R.id.webView).canGoBack()
                filesDir = activity.filesDir
            }
            assertTrue("in-session WebView back navigation is retained", canGoBack)
            assertFalse(File(requireNotNull(filesDir), "history.json").exists())
        }
    }

    @Test fun addressAndSearchTextAreNotRestoredAfterActivityRestart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val privateQuery = "https://search.private-test/?q=prior-private-query"
                val homeInput = activity.findViewById<EditText>(R.id.homeUrlInput)
                val chromeInput = activity.findViewById<EditText>(R.id.urlInput)
                homeInput.setText(privateQuery)

                assertFalse(homeInput.isSaveEnabled)
                assertFalse(chromeInput.isSaveEnabled)
                assertTrue((homeInput.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0)
                assertTrue((chromeInput.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, homeInput.importantForAutofill)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, chromeInput.importantForAutofill)
            }

            scenario.recreate()
            scenario.onActivity { restarted ->
                assertEquals("", restarted.findViewById<EditText>(R.id.homeUrlInput).text.toString())
                assertEquals("", restarted.findViewById<EditText>(R.id.urlInput).text.toString())
                assertFalse(File(restarted.filesDir, "history.json").exists())
            }
        }
    }

    @Test fun favoritesRemainAfterActivityRestart() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val page = LocalPageServer("favorite origin", "Kept favorite")
        try {
            val favoriteUrl = page.url
            loadPage(scenario, page)

            var favoritesFile: File? = null
            var currentUrl: String? = null
            var clickHandled = false
            scenario.onActivity { activity ->
                favoritesFile = File(activity.filesDir, "favorites.json")
                currentUrl = activity.findViewById<WebView>(R.id.webView).url
                activity.findViewById<View>(R.id.chromeBar).visibility = View.VISIBLE
                clickHandled = activity.findViewById<View>(R.id.favoriteButton).performClick()
            }
            assertTrue("favorite control receives the click at $currentUrl", clickHandled)
            val file = requireNotNull(favoritesFile)
            assertTrue("favorite write completes", await(scenario) {
                file.exists() && FavoritesJson.fromJson(file.readText())
                    .contains(Favorite("Kept favorite", favoriteUrl))
            })

            scenario.recreate()
            var visible = false
            scenario.onActivity { activity ->
                val home = activity.findViewById<HomeView>(R.id.homeView)
                visible = home.findViewWithTag<View>(favoriteUrl) != null
                assertTrue(JsonFavoritesStore(file).findAll().contains(Favorite("Kept favorite", favoriteUrl)))
            }
            assertTrue("saved favorite is visible after restart", visible)
        } finally {
            page.close()
            scenario.close()
        }
    }

    @Test fun backAndForwardButtonsNavigateWithinTheCurrentSession() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val first = LocalPageServer("first origin", "First")
            val second = LocalPageServer("second origin", "Second")
            try {
                loadPage(scenario, first)
                loadPage(scenario, second)

                var canGoBack = false
                var canGoForward = true
                scenario.onActivity { activity ->
                    val webView = activity.findViewById<WebView>(R.id.webView)
                    canGoBack = webView.canGoBack()
                    canGoForward = webView.canGoForward()
                    assertTrue(activity.findViewById<View>(R.id.backButton).isEnabled)
                    assertFalse(activity.findViewById<View>(R.id.forwardButton).isEnabled)
                }
                assertTrue(canGoBack)
                assertFalse(canGoForward)

                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.backButton).performClick()
                }
                assertTrue("Back returns to the prior page", await(scenario) {
                    it.findViewById<WebView>(R.id.webView).url == first.url
                })
                assertTrue("Forward becomes available after Back", await(scenario) {
                    it.findViewById<View>(R.id.forwardButton).isEnabled
                })
                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.forwardButton).performClick()
                }
                assertTrue("Forward returns to the second page", await(scenario) {
                    it.findViewById<WebView>(R.id.webView).url == second.url
                })
            } finally {
                first.close()
                second.close()
            }
        }
    }

    private fun loadPage(scenario: ActivityScenario<MainActivity>, page: LocalPageServer) {
        scenario.onActivity { activity ->
            activity.findViewById<WebView>(R.id.webView).loadUrl(page.url)
        }
        var state = "unavailable"
        val loaded = await(scenario) {
            val webView = it.findViewById<WebView>(R.id.webView)
            state = "url=${webView.url}, title=${webView.title}"
            webView.url == page.url && webView.title == page.title
        }
        assertTrue("local test page loads: ${page.url} ($state)", loaded)
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 5_000L,
        predicate: (MainActivity) -> Boolean
    ): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var matched = false
            scenario.onActivity { matched = predicate(it) }
            if (matched) return true
            Thread.sleep(25L)
        }
        return false
    }

    /** Tiny localhost-only HTTP origin so browser tests never contact an external service. */
    private class LocalPageServer(private val name: String, val title: String) : Closeable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}/${name.replace(' ', '-')}"
        private val worker = Thread({ serveUntilClosed() }, "private-test-server-$name").apply {
            isDaemon = true
            start()
        }

        private fun serveUntilClosed() {
            while (!server.isClosed) {
                try {
                    server.accept().use(::respond)
                } catch (_: Exception) {
                    if (!server.isClosed) throw AssertionError("Local test server failed")
                }
            }
        }

        private fun respond(socket: Socket) {
            val request = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            while (request.readLine()?.isNotEmpty() == true) Unit
            val body = "<html><head><title>$title</title></head><body>$title</body></html>"
                .toByteArray(Charsets.UTF_8)
            val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(headers.toByteArray(Charsets.US_ASCII))
                write(body)
                flush()
            }
        }

        override fun close() {
            server.close()
            worker.join(1_000L)
        }
    }
}
