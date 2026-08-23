package fr.vriege.anilib.platform.compose

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

internal class LoopbackOAuthReceiver(
    private val callbackUri: URI,
) : AutoCloseable {
    private val callbackPath = callbackUri.rawPath
    private val completionPath = "$callbackPath/complete"
    private val server = ServerSocket()

    init {
        require(callbackUri.scheme.equals("http", ignoreCase = true)) {
            "OAuth loopback callback must use HTTP"
        }
        require(callbackUri.host == LOOPBACK_HOST && callbackUri.port in 1..65535) {
            "OAuth loopback callback must use an explicit 127.0.0.1 port"
        }
        require(
            callbackPath.matches(Regex("/[A-Za-z0-9/_-]+")) &&
                callbackUri.rawQuery == null &&
                callbackUri.rawFragment == null,
        ) {
            "OAuth loopback callback must have a plain absolute path"
        }
        server.reuseAddress = false
        server.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), callbackUri.port), BACKLOG)
        server.soTimeout = ACCEPT_TIMEOUT_MILLIS
    }

    suspend fun awaitCallback(): URI {
        while (currentCoroutineContext().isActive) {
            val socket = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            }
            socket.use { connection ->
                connection.soTimeout = REQUEST_TIMEOUT_MILLIS
                receive(connection)?.let { return it }
            }
        }
        throw IllegalStateException("OAuth login was cancelled")
    }

    override fun close() {
        runCatching { server.close() }
    }

    private fun receive(socket: Socket): URI? {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        val requestLine = reader.readLine() ?: return null
        val request = requestLine.split(' ')
        if (request.size != 3 || request[2] != "HTTP/1.1") {
            respond(socket, 400, "Bad Request", TEXT_HEADERS, "Invalid request")
            return null
        }
        val headers = readHeaders(reader)
        return when {
            request[0] == "GET" && request[1] == callbackPath -> {
                respond(socket, 200, "OK", HTML_HEADERS, callbackPage(completionPath))
                null
            }
            request[0] == "POST" && request[1] == completionPath -> {
                val length = headers["content-length"]?.toIntOrNull() ?: -1
                if (length !in 1..MAXIMUM_RESULT_LENGTH) {
                    respond(socket, 400, "Bad Request", TEXT_HEADERS, "Invalid OAuth result")
                    return null
                }
                val result = readBody(reader, length)
                if (result.length != length || result.any { it == '\r' || it == '\n' }) {
                    respond(socket, 400, "Bad Request", TEXT_HEADERS, "Invalid OAuth result")
                    return null
                }
                respond(socket, 204, "No Content", TEXT_HEADERS, "")
                URI.create("${callbackUri.toASCIIString()}#$result")
            }
            else -> {
                respond(socket, 404, "Not Found", TEXT_HEADERS, "Not found")
                null
            }
        }
    }

    private fun readHeaders(reader: BufferedReader): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        repeat(MAXIMUM_HEADER_LINES) {
            val line = reader.readLine() ?: return headers
            if (line.isEmpty()) return headers
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers.putIfAbsent(
                    line.substring(0, separator).trim().lowercase(),
                    line.substring(separator + 1).trim(),
                )
            }
        }
        return headers
    }

    private fun readBody(reader: BufferedReader, length: Int): String {
        val body = CharArray(length)
        var offset = 0
        while (offset < length) {
            val read = reader.read(body, offset, length - offset)
            if (read < 0) break
            offset += read
        }
        return String(body, 0, offset)
    }

    private fun respond(
        socket: Socket,
        status: Int,
        reason: String,
        headers: String,
        body: String,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append(headers)
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        socket.getOutputStream().apply {
            write(response)
            write(bytes)
            flush()
        }
    }

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val BACKLOG = 4
        const val ACCEPT_TIMEOUT_MILLIS = 500
        const val REQUEST_TIMEOUT_MILLIS = 2_000
        const val MAXIMUM_HEADER_LINES = 40
        const val MAXIMUM_RESULT_LENGTH = 16_384
        const val TEXT_HEADERS = "Content-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\n"
        const val HTML_HEADERS = "Content-Type: text/html; charset=utf-8\r\n" +
            "Cache-Control: no-store\r\n" +
            "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; " +
            "style-src 'unsafe-inline'; connect-src 'self'\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\n"

        fun callbackPage(completionPath: String): String = """
            <!doctype html>
            <html lang="fr">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Connexion AniList</title>
              <style>
                :root { color-scheme: light dark; font-family: system-ui, sans-serif; }
                body { min-height: 100vh; margin: 0; display: grid; place-items: center; background: #111016; }
                main { max-width: 32rem; padding: 2rem; text-align: center; color: #f2eef8; }
                .mark { font-size: 3rem; color: #02a9ff; }
                p { color: #c9c2d2; line-height: 1.5; }
              </style>
            </head>
            <body>
              <main>
                <div class="mark">A</div>
                <h1 id="title">Finalisation de la connexion…</h1>
                <p id="message">Cette page transmet le résultat uniquement à Anilib sur cet appareil.</p>
              </main>
              <script>
                const payload = location.hash.slice(1);
                const title = document.getElementById('title');
                const message = document.getElementById('message');
                if (!payload) {
                  title.textContent = 'Connexion incomplète';
                  message.textContent = 'AniList n’a renvoyé aucun résultat. Fermez cette page puis réessayez.';
                } else {
                  fetch('$completionPath', {
                    method: 'POST',
                    headers: {'Content-Type': 'text/plain;charset=UTF-8'},
                    body: payload,
                  }).then(response => {
                    if (!response.ok) throw new Error('callback rejected');
                    history.replaceState(null, '', location.pathname);
                    title.textContent = 'Connexion réussie';
                    message.textContent = 'Vous pouvez fermer cette page et retourner dans Anilib.';
                  }).catch(() => {
                    title.textContent = 'Anilib ne répond plus';
                    message.textContent = 'Retournez dans Anilib puis relancez la connexion.';
                  });
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
