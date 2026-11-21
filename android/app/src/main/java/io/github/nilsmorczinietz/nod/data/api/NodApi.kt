package io.github.nilsmorczinietz.nod.data.api

import io.github.nilsmorczinietz.nod.data.model.ErrorResponse
import io.github.nilsmorczinietz.nod.data.model.HubSettings
import io.github.nilsmorczinietz.nod.data.model.Item
import io.github.nilsmorczinietz.nod.data.model.ItemsResponse
import io.github.nilsmorczinietz.nod.data.model.MeResponse
import io.github.nilsmorczinietz.nod.data.model.PushTokenInput
import io.github.nilsmorczinietz.nod.data.model.RegisterDeviceInput
import io.github.nilsmorczinietz.nod.data.model.RegisterDeviceResponse
import io.github.nilsmorczinietz.nod.data.model.ResolveInput
import io.github.nilsmorczinietz.nod.data.model.SettingsPatch
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Relay base URL plus device bearer token. */
data class Auth(val relayUrl: String, val token: String)

/** Non-2xx answer from the relay. [code] is the machine-readable `error` field. */
class ApiException(
    val status: Int,
    val code: String,
    message: String,
    val body: ErrorResponse? = null,
) : IOException(message)

/** Outcome of answering an item: either we won, or someone else was faster. */
sealed interface ResolveResult {
    val item: Item

    data class Resolved(override val item: Item) : ResolveResult
    data class AlreadyFinished(override val item: Item) : ResolveResult
}

/** Typed, coroutine-friendly client for the relay REST API. */
class NodApi(
    private val http: OkHttpClient,
    private val json: Json,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun register(relayUrl: String, input: RegisterDeviceInput): RegisterDeviceResponse =
        call(Request.Builder().url(url(relayUrl, "/v1/devices")).post(body(input)).build())

    suspend fun me(auth: Auth): MeResponse = call(get(auth, "/v1/me"))

    suspend fun items(auth: Auth, limit: Int = 100): List<Item> =
        call<ItemsResponse>(get(auth, "/v1/items?limit=$limit")).items

    suspend fun item(auth: Auth, id: String): Item = call(get(auth, "/v1/items/$id"))

    suspend fun resolve(auth: Auth, id: String, input: ResolveInput): ResolveResult = try {
        ResolveResult.Resolved(call(authed(auth, "/v1/items/$id/resolve").post(body(input)).build()))
    } catch (e: ApiException) {
        val item = e.body?.item
        if (e.status == 409 && item != null) ResolveResult.AlreadyFinished(item) else throw e
    }

    suspend fun updateSettings(auth: Auth, patch: SettingsPatch): HubSettings =
        call(authed(auth, "/v1/settings").patch(body(patch)).build())

    suspend fun putPushToken(auth: Auth, token: String) {
        callUnit(authed(auth, "/v1/me/push-token").put(body(PushTokenInput(token))).build())
    }

    suspend fun unpair(auth: Auth) {
        callUnit(authed(auth, "/v1/me").delete().build())
    }

    // ---------------------------------------------------------------------

    private fun url(relayUrl: String, path: String) = "${relayUrl.trimEnd('/')}$path".toHttpUrl()

    private fun authed(auth: Auth, path: String): Request.Builder =
        Request.Builder().url(url(auth.relayUrl, path)).header("Authorization", "Bearer ${auth.token}")

    private fun get(auth: Auth, path: String): Request = authed(auth, path).get().build()

    private inline fun <reified T> body(value: T): RequestBody =
        json.encodeToString(serializer<T>(), value).toRequestBody(jsonType)

    private suspend inline fun <reified T> call(request: Request): T {
        val text = execute(request)
        return json.decodeFromString(serializer<T>(), text)
    }

    private suspend fun callUnit(request: Request) {
        execute(request)
    }

    /** Runs the call and reads the body on the IO dispatcher (body reads block). */
    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response = http.newCall(request).await()
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) {
                val error = runCatching { json.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
                throw ApiException(
                    status = it.code,
                    code = error?.error ?: "http_${it.code}",
                    message = error?.message ?: "HTTP ${it.code}",
                    body = error,
                )
            }
            text
        }
    }
}

/** Suspends until the call completes; cancelling the coroutine cancels the HTTP call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation { cancel() }
}
