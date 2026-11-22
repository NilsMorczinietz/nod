package io.github.nilsmorczinietz.nod.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Kotlin mirror of `packages/protocol/src/index.ts`. Keep both in sync;
 * unknown fields are ignored so the relay can evolve without breaking the app.
 */
const val PROTOCOL_VERSION = 1

val NodJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
    classDiscriminator = "type"
}

@Serializable
enum class ItemKind {
    @SerialName("permission") Permission,
    @SerialName("question") Question,
    @SerialName("plan") Plan,
    @SerialName("completed") Completed,
    @SerialName("failed") Failed,
    @SerialName("attention") Attention;

    val isInteractive: Boolean get() = this == Permission || this == Question || this == Plan
}

@Serializable
enum class ItemStatus {
    @SerialName("pending") Pending,
    @SerialName("resolved") Resolved,
    @SerialName("expired") Expired,
    @SerialName("cancelled") Cancelled,
    @SerialName("info") Info,
}

@Serializable
enum class Decision {
    @SerialName("allow") Allow,
    @SerialName("deny") Deny,
    @SerialName("answer") Answer,
}

@Serializable
data class SessionRef(
    val id: String,
    val project: String,
    val cwd: String,
    val host: String,
    /** Display name of the asking agent ("Claude Code", "Cursor", "Script"); missing = Claude Code. */
    val agent: String? = null,
) {
    val agentName: String get() = agentOrDefault(agent)
}

/** Agent name shown when the relay sends none (older hooks are always Claude Code). */
const val DEFAULT_AGENT = "Claude Code"

fun agentOrDefault(agent: String?): String = agent?.takeIf { it.isNotBlank() } ?: DEFAULT_AGENT

@Serializable
data class PermissionDetails(
    val tool: String,
    val summary: String,
    val detail: String? = null,
    val canAlwaysAllow: Boolean = false,
)

@Serializable
data class QuestionOption(
    val label: String,
    val description: String? = null,
)

@Serializable
data class Question(
    val question: String,
    val header: String = "",
    val multiSelect: Boolean = false,
    val options: List<QuestionOption> = emptyList(),
    /** A free-text reply is allowed besides the options (sent as ResolveInput.message). */
    val allowText: Boolean = false,
)

@Serializable
data class PlanDetails(val markdown: String)

@Serializable
data class Resolution(
    val decision: Decision,
    val alwaysAllow: Boolean? = null,
    val answers: Map<String, List<String>>? = null,
    val message: String? = null,
    val deviceId: String,
    val deviceName: String,
    val at: Long,
)

@Serializable
data class Item(
    val id: String,
    val kind: ItemKind,
    val status: ItemStatus,
    val session: SessionRef,
    val title: String,
    val body: String? = null,
    val permission: PermissionDetails? = null,
    val questions: List<Question>? = null,
    val plan: PlanDetails? = null,
    val createdAt: Long,
    val expiresAt: Long,
    val resolution: Resolution? = null,
)

@Serializable
data class ResolveInput(
    val decision: Decision,
    val alwaysAllow: Boolean? = null,
    val answers: Map<String, List<String>>? = null,
    val message: String? = null,
)

@Serializable
data class Device(
    val id: String,
    val name: String,
    val kind: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val hasPushToken: Boolean = false,
)

/** Public Firebase client config delivered by the relay at pairing time. */
@Serializable
data class FirebaseClientConfig(
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val senderId: String,
)

@Serializable
data class RegisterDeviceInput(
    val code: String,
    val name: String,
    val kind: String = "android",
)

@Serializable
data class RegisterDeviceResponse(
    val device: Device,
    val token: String,
    val firebase: FirebaseClientConfig? = null,
)

/** Where requests go; see ROUTING_MODES in the protocol. */
@Serializable
enum class RoutingMode {
    /** Desktop always; the phone only while no desktop reports the user as active. */
    @SerialName("away") Away,

    /** Desktop and phone. */
    @SerialName("always") Always,

    /** Only informational items; requests stay in the terminal. */
    @SerialName("updates") Updates,

    /** Nothing is delivered (a.k.a. paused). */
    @SerialName("terminal") Terminal,
}

@Serializable
data class HubSettings(
    val mode: RoutingMode = RoutingMode.Away,
    val paused: Boolean = false,
)

/** PATCH /v1/settings; `paused = false` restores the previous mode. */
@Serializable
data class SettingsPatch(
    val mode: RoutingMode? = null,
    val paused: Boolean? = null,
)

@Serializable
data class PushTokenInput(val pushToken: String)

@Serializable
data class MeResponse(val device: Device, val settings: HubSettings)

@Serializable
data class ItemsResponse(val items: List<Item>)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String? = null,
    val item: Item? = null,
)

/** Messages on the `/v1/stream` WebSocket, discriminated by `type`. */
@Serializable
sealed interface StreamMessage {
    @Serializable
    @SerialName("hello")
    data class Hello(
        val protocol: Int,
        val pending: List<Item>,
        val settings: HubSettings,
    ) : StreamMessage

    @Serializable
    @SerialName("item")
    data class NewItem(val item: Item) : StreamMessage

    @Serializable
    @SerialName("update")
    data class Update(val item: Item) : StreamMessage

    @Serializable
    @SerialName("settings")
    data class Settings(val settings: HubSettings) : StreamMessage
}
