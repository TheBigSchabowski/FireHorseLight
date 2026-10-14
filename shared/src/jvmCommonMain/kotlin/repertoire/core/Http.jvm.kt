package repertoire.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

actual suspend fun httpGet(url: String, token: String?): HttpResponse = withContext(Dispatchers.IO) {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        // lichess asks apps to say who they are, so it can get in touch instead of blocking.
        connection.setRequestProperty("User-Agent", "FireHorse (https://github.com/TheBigSchabowski/FireHorse)")
        if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        HttpResponse(status, stream?.bufferedReader()?.use { it.readText() } ?: "")
    } finally {
        connection.disconnect()
    }
}
