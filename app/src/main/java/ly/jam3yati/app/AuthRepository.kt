package ly.jam3yati.app

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.FlowType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object JamSupabase {
    const val URL = "https://irknxbbmhibutriaagfw.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable__YUC1HHjJAtdYqQUcwecgQ_H0-RuL1g"

    val client = createSupabaseClient(URL, PUBLISHABLE_KEY) {
        install(Auth) { flowType = FlowType.PKCE }
        install(Postgrest)
    }
}

@Serializable
data class JamSubscription(
    val id: String,
    @SerialName("user_id") val userId: String,
    val plan: String = "pro",
    val status: String = "trialing",
    @SerialName("trial_started_at") val trialStartedAt: String? = null,
    @SerialName("trial_ends_at") val trialEndsAt: String? = null,
    val amount: Double = 0.0,
    val currency: String = "LYD",
    @SerialName("next_billing_at") val nextBillingAt: String? = null,
    val provider: String? = null
)

class AuthRepository {
    val client = JamSupabase.client

    suspend fun currentUser(): UserInfo? = client.auth.currentUserOrNull()

    suspend fun signIn(email: String, password: String) {
        require(email.isNotBlank()) { "أدخل البريد الإلكتروني" }
        require(password.length >= 6) { "كلمة المرور يجب ألا تقل عن 6 أحرف" }
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(fullName: String, phone: String, email: String, password: String) {
        require(fullName.isNotBlank()) { "أدخل الاسم" }
        require(email.isNotBlank()) { "أدخل البريد الإلكتروني" }
        require(password.length >= 6) { "كلمة المرور يجب ألا تقل عن 6 أحرف" }
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("full_name", fullName.trim())
                put("phone", phone.trim())
            }
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun subscription(): JamSubscription? {
        val user = currentUser() ?: return null
        return runCatching {
            client.postgrest["subscriptions"]
                .select { eq("user_id", user.id) }
                .decodeSingle<JamSubscription>()
        }.getOrNull()
    }
}
