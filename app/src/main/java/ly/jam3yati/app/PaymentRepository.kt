package ly.jam3yati.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PaymentRepository(private val auth: AuthRepository) {
    suspend fun createCheckoutUrl(): String = withContext(Dispatchers.IO) {
        val session = auth.client.auth.currentSessionOrNull() ?: error("انتهت جلسة الدخول")
        val connection = (URL("\${JamSupabase.URL}/functions/v1/create-subscription-payment").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer \${session.accessToken}")
        connection.setRequestProperty("apikey", JamSupabase.PUBLISHABLE_KEY)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write("{}".toByteArray()) }

        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val json = JSONObject(body)
        if (!json.has("checkout_url")) error(json.optString("error", "تعذر إنشاء عملية الدفع"))
        json.getString("checkout_url")
    }
}
