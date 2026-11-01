package com.glassbox.rotaportal.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpsgenieClient(
    private val baseUrl: String,
    private val tokenStore: TokenStore,
    private val httpClient: HttpClient = opsgenieHttpClient(tokenStore),
) {
    suspend fun validateStoredToken(): TokenValidationResult {
        if (!tokenStore.hasToken()) {
            return TokenValidationResult.Missing
        }

        return validateTokenWithApi()
    }

    suspend fun saveAndValidateToken(token: String): TokenValidationResult {
        val normalizedToken = token.trim()
        if (normalizedToken.isEmpty()) {
            return TokenValidationResult.Invalid("Token must not be empty.")
        }

        tokenStore.saveToken(normalizedToken)
        return validateTokenWithApi()
    }

    fun clearToken() {
        tokenStore.clearToken()
    }

    suspend fun listOverrides(scheduleName: String): List<OpsgenieOverride> {
        val response = httpClient.get(
            "${baseUrl.trimEnd('/')}/v2/schedules/${scheduleName.urlPathEncode()}/overrides?scheduleIdentifierType=name",
        )
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            tokenStore.clearToken()
            throw OpsgenieApiException("Opsgenie rejected the stored token.")
        }
        if (!response.status.isSuccess()) {
            throw OpsgenieApiException("Opsgenie returned HTTP ${response.status.value}.")
        }

        return response.body<OverridesResponse>().data.orEmpty().map { override ->
            OpsgenieOverride(
                alias = override.alias.orEmpty(),
                member = TeamDirectory.memberForUsername(override.user?.username),
                username = override.user?.username,
                start = override.startDate.orEmpty(),
                end = override.endDate.orEmpty(),
            )
        }.filter { override ->
            override.start.isNotBlank() && override.end.isNotBlank()
        }
    }

    suspend fun getFinalTimeline(
        scheduleName: String,
        rotationName: String,
        date: String,
        interval: Int = 1,
        intervalUnit: String = "months",
    ): List<OnCallPeriod> {
        val response = httpClient.get(
            "${baseUrl.trimEnd('/')}/v2/schedules/${scheduleName.urlPathEncode()}/timeline" +
                "?identifierType=name&interval=$interval&intervalUnit=$intervalUnit&date=${date.urlPathEncode()}",
        )
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            tokenStore.clearToken()
            throw OpsgenieApiException("Opsgenie rejected the stored token.")
        }
        if (!response.status.isSuccess()) {
            throw OpsgenieApiException("Opsgenie returned HTTP ${response.status.value}.")
        }

        val rotations = response.body<TimelineResponse>().data?.finalTimeline?.rotations.orEmpty()
        return rotations
            .filter { rotation -> rotation.name.isNullOrBlank() || rotation.name == rotationName }
            .flatMap { rotation ->
                rotation.periods.orEmpty().mapNotNull { period ->
                    val start = normalizeIsoZ(period.startDate) ?: return@mapNotNull null
                    val end = normalizeIsoZ(period.endDate) ?: return@mapNotNull null
                    val username = period.recipient?.name
                    val member = if (username.isNullOrBlank()) {
                        "Unassigned"
                    } else {
                        TeamDirectory.memberForUsername(username)
                    }
                    val type = period.type ?: "default"
                    OnCallPeriod(
                        id = "${start}_${end}_${member}_$type".replace(":", ""),
                        member = member,
                        username = username,
                        start = start,
                        end = end,
                        type = type,
                    )
                }
            }
            .sortedBy { it.start }
    }

    suspend fun createOrUpdateOverride(
        scheduleName: String,
        rotationName: String,
        request: OverrideMutationRequest,
        carve: Boolean = true,
    ): OverrideMutationResult {
        val carved = if (carve) {
            carveOverlappingOverrides(scheduleName, rotationName, request.start, request.end)
        } else {
            emptyList()
        }

        val existingAlias = listOverrides(scheduleName)
            .firstOrNull { sameWindow(it.start, it.end, request.start, request.end) }
            ?.alias
            ?.takeIf { it.isNotBlank() }
        val alias = existingAlias ?: makeAlias(request.start, request.end, if (request.partial) "partial" else "")

        if (existingAlias != null) {
            updateOverride(scheduleName, rotationName, request, alias)
            return request.toMutationResult(OverrideOperation.Updated, alias, carved.size)
        }

        val createResponse = createOverride(scheduleName, rotationName, request, alias)
        if (createResponse.status == HttpStatusCode.UnprocessableEntity) {
            updateOverride(scheduleName, rotationName, request, alias)
            return request.toMutationResult(OverrideOperation.Updated, alias, carved.size)
        }
        createResponse.requireSuccess()
        return request.toMutationResult(OverrideOperation.Created, alias, carved.size)
    }

    private suspend fun carveOverlappingOverrides(
        scheduleName: String,
        rotationName: String,
        start: String,
        end: String,
    ): List<String> {
        val requestedStart = Instant.parse(start)
        val requestedEnd = Instant.parse(end)
        val carvedAliases = mutableListOf<String>()

        for (override in listOverrides(scheduleName)) {
            val alias = override.alias.takeIf { it.isNotBlank() } ?: continue
            val username = override.username?.takeIf { it.isNotBlank() } ?: continue
            val existingStart = override.start.toInstantOrNull() ?: continue
            val existingEnd = override.end.toInstantOrNull() ?: continue

            if (existingEnd <= requestedStart || existingStart >= requestedEnd) continue
            if (existingStart == requestedStart && existingEnd == requestedEnd) continue

            deleteOverride(scheduleName, alias)
            carvedAliases += alias

            if (existingStart < requestedStart) {
                val leftStart = existingStart.toIsoZ()
                val leftEnd = requestedStart.toIsoZ()
                ensureRemainderOverride(scheduleName, rotationName, username, leftStart, leftEnd)
            }
            if (requestedEnd < existingEnd) {
                val rightStart = requestedEnd.toIsoZ()
                val rightEnd = existingEnd.toIsoZ()
                ensureRemainderOverride(scheduleName, rotationName, username, rightStart, rightEnd)
            }
        }
        return carvedAliases
    }

    private suspend fun ensureRemainderOverride(
        scheduleName: String,
        rotationName: String,
        username: String,
        start: String,
        end: String,
    ) {
        val existing = listOverrides(scheduleName).firstOrNull { sameWindow(it.start, it.end, start, end) }
        if (existing != null) return

        val request = OverrideMutationRequest(
            member = TeamDirectory.memberForUsername(username),
            username = username,
            start = start,
            end = end,
            partial = true,
        )
        val response = createOverride(
            scheduleName = scheduleName,
            rotationName = rotationName,
            request = request,
            alias = makeAlias(start, end, "remainder"),
        )
        response.requireSuccess()
    }

    private suspend fun createOverride(
        scheduleName: String,
        rotationName: String,
        request: OverrideMutationRequest,
        alias: String,
    ): HttpResponse {
        return httpClient.post(
            "${baseUrl.trimEnd('/')}/v2/schedules/${scheduleName.urlPathEncode()}/overrides?scheduleIdentifierType=name",
        ) {
            contentType(ContentType.Application.Json)
            setBody(request.toOpsgeniePayload(rotationName, alias))
        }
    }

    private suspend fun updateOverride(
        scheduleName: String,
        rotationName: String,
        request: OverrideMutationRequest,
        alias: String,
    ) {
        val response = httpClient.put(
            "${baseUrl.trimEnd('/')}/v2/schedules/${scheduleName.urlPathEncode()}/overrides/${alias.urlPathEncode()}?scheduleIdentifierType=name",
        ) {
            contentType(ContentType.Application.Json)
            setBody(request.toOpsgeniePayload(rotationName, alias = null))
        }
        response.requireSuccess()
    }

    private suspend fun deleteOverride(scheduleName: String, alias: String) {
        val response = httpClient.delete(
            "${baseUrl.trimEnd('/')}/v2/schedules/${scheduleName.urlPathEncode()}/overrides/${alias.urlPathEncode()}?scheduleIdentifierType=name",
        )
        response.requireSuccess()
    }

    private suspend fun validateTokenWithApi(): TokenValidationResult {
        return try {
            val response = httpClient.get("${baseUrl.trimEnd('/')}/v2/account")
            response.toValidationResult()
        } catch (exc: CancellationException) {
            throw exc
        } catch (exc: Exception) {
            TokenValidationResult.Error("Network error while validating the token.")
        }
    }

    private suspend fun HttpResponse.toValidationResult(): TokenValidationResult {
        return when (status) {
            HttpStatusCode.OK -> {
                val account = body<AccountResponse>()
                TokenValidationResult.Valid(accountName = account.data?.name)
            }
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden -> {
                tokenStore.clearToken()
                TokenValidationResult.Invalid("Opsgenie rejected the token. Paste a valid API token.")
            }
            else -> TokenValidationResult.Error("Validation failed with HTTP ${status.value}.")
        }
    }
}

class OpsgenieApiException(message: String) : RuntimeException(message)

fun opsgenieHttpClient(tokenStore: TokenStore): HttpClient {
    return HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
        }
        defaultRequest {
            tokenStore.getToken()?.let { token ->
                header(HttpHeaders.Authorization, "GenieKey $token")
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
    }
}

sealed interface TokenValidationResult {
    data class Valid(val accountName: String?) : TokenValidationResult
    data object Missing : TokenValidationResult
    data class Invalid(val message: String) : TokenValidationResult
    data class Error(val message: String) : TokenValidationResult
}

@Serializable
private data class AccountResponse(
    @SerialName("data")
    val data: AccountData? = null,
)

@Serializable
private data class AccountData(
    @SerialName("name")
    val name: String? = null,
)

@Serializable
private data class OverridesResponse(
    @SerialName("data")
    val data: List<OverridePayload>? = null,
)

@Serializable
private data class TimelineResponse(
    @SerialName("data")
    val data: TimelineData? = null,
)

@Serializable
private data class TimelineData(
    @SerialName("finalTimeline")
    val finalTimeline: FinalTimeline? = null,
)

@Serializable
private data class FinalTimeline(
    @SerialName("rotations")
    val rotations: List<TimelineRotation>? = null,
)

@Serializable
private data class TimelineRotation(
    @SerialName("name")
    val name: String? = null,
    @SerialName("periods")
    val periods: List<TimelinePeriod>? = null,
)

@Serializable
private data class TimelinePeriod(
    @SerialName("startDate")
    val startDate: String? = null,
    @SerialName("endDate")
    val endDate: String? = null,
    @SerialName("type")
    val type: String? = null,
    @SerialName("recipient")
    val recipient: TimelineRecipient? = null,
)

@Serializable
private data class TimelineRecipient(
    @SerialName("name")
    val name: String? = null,
)

@Serializable
private data class OverridePayload(
    @SerialName("alias")
    val alias: String? = null,
    @SerialName("user")
    val user: OverrideUser? = null,
    @SerialName("startDate")
    val startDate: String? = null,
    @SerialName("endDate")
    val endDate: String? = null,
)

@Serializable
private data class OverrideUser(
    @SerialName("username")
    val username: String? = null,
)

@Serializable
private data class OpsgenieOverrideRequestPayload(
    @SerialName("user")
    val user: OpsgenieOverrideUserPayload,
    @SerialName("startDate")
    val startDate: String,
    @SerialName("endDate")
    val endDate: String,
    @SerialName("rotations")
    val rotations: List<OpsgenieRotationPayload>,
    @SerialName("alias")
    val alias: String? = null,
)

@Serializable
private data class OpsgenieOverrideUserPayload(
    @SerialName("type")
    val type: String,
    @SerialName("username")
    val username: String,
)

@Serializable
private data class OpsgenieRotationPayload(
    @SerialName("name")
    val name: String,
)

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

private fun OverrideMutationRequest.toOpsgeniePayload(
    rotationName: String,
    alias: String?,
): OpsgenieOverrideRequestPayload {
    return OpsgenieOverrideRequestPayload(
        user = OpsgenieOverrideUserPayload(
            type = "user",
            username = username,
        ),
        startDate = start,
        endDate = end,
        rotations = listOf(OpsgenieRotationPayload(rotationName)),
        alias = alias,
    )
}

private fun OverrideMutationRequest.toMutationResult(
    operation: OverrideOperation,
    alias: String,
    carvedCount: Int = 0,
): OverrideMutationResult {
    return OverrideMutationResult(
        operation = operation,
        alias = alias,
        override = OpsgenieOverride(
            alias = alias,
            member = member,
            username = username,
            start = start,
            end = end,
        ),
        carvedCount = carvedCount,
    )
}

private fun makeAlias(start: String, end: String, suffix: String): String {
    val compactStart = start.replace(":", "").replace("-", "").replace("T", "-").replace("Z", "")
    val compactEnd = end.replace(":", "").replace("-", "").replace("T", "-").replace("Z", "")
    val base = "rota-portal-$compactStart-$compactEnd"
    return if (suffix.isBlank()) base else "$base-$suffix"
}

private fun sameWindow(leftStart: String, leftEnd: String, rightStart: String, rightEnd: String): Boolean {
    val a = leftStart.toInstantOrNull() ?: return leftStart == rightStart && leftEnd == rightEnd
    val b = leftEnd.toInstantOrNull() ?: return leftStart == rightStart && leftEnd == rightEnd
    val c = rightStart.toInstantOrNull() ?: return false
    val d = rightEnd.toInstantOrNull() ?: return false
    return a == c && b == d
}

private fun String.toInstantOrNull(): Instant? {
    return try {
        Instant.parse(this)
    } catch (_: Exception) {
        null
    }
}

private fun Instant.toIsoZ(): String {
    return toString().replace(Regex("\\.\\d+Z$"), "Z")
}

private fun HttpResponse.requireSuccess() {
    if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
        throw OpsgenieApiException("Opsgenie rejected the stored token.")
    }
    if (!status.isSuccess()) {
        throw OpsgenieApiException("Opsgenie returned HTTP ${status.value}.")
    }
}

private fun String.urlPathEncode(): String {
    val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    return buildString {
        for (char in this@urlPathEncode) {
            if (char in allowed) {
                append(char)
            } else {
                append('%')
                append(char.code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}

private fun normalizeIsoZ(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return value.trim().toInstantOrNull()?.toIsoZ()
}
