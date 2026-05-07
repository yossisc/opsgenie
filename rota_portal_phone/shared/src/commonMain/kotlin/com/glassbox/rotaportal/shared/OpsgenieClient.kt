package com.glassbox.rotaportal.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
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

    suspend fun createOrUpdateOverride(
        scheduleName: String,
        rotationName: String,
        request: OverrideMutationRequest,
    ): OverrideMutationResult {
        val existingAlias = listOverrides(scheduleName)
            .firstOrNull { it.start == request.start && it.end == request.end }
            ?.alias
            ?.takeIf { it.isNotBlank() }
        val alias = existingAlias ?: makeAlias(request.start, request.end, if (request.partial) "partial" else "")

        if (existingAlias != null) {
            updateOverride(scheduleName, rotationName, request, alias)
            return request.toMutationResult(OverrideOperation.Updated, alias)
        }

        val createResponse = createOverride(scheduleName, rotationName, request, alias)
        if (createResponse.status == HttpStatusCode.UnprocessableEntity) {
            updateOverride(scheduleName, rotationName, request, alias)
            return request.toMutationResult(OverrideOperation.Updated, alias)
        }
        createResponse.requireSuccess()
        return request.toMutationResult(OverrideOperation.Created, alias)
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
    )
}

private fun makeAlias(start: String, end: String, suffix: String): String {
    val compactStart = start.replace(":", "").replace("-", "").replace("T", "-").replace("Z", "")
    val compactEnd = end.replace(":", "").replace("-", "").replace("T", "-").replace("Z", "")
    val base = "rota-portal-$compactStart-$compactEnd"
    return if (suffix.isBlank()) base else "$base-$suffix"
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
