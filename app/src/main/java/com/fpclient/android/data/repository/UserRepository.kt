package com.fpclient.android.data.repository

import android.content.Context
import android.net.Uri
import com.fpclient.android.data.dto.ActorDto
import com.fpclient.android.data.dto.ChangePasswordRequest
import com.fpclient.android.data.dto.EmailChangeStatusResponse
import com.fpclient.android.data.dto.FollowStatusDto
import com.fpclient.android.data.dto.HeatmapFeatureCollectionDto
import com.fpclient.android.data.dto.HeatmapMapper
import com.fpclient.android.data.dto.HeatmapResponse
import com.fpclient.android.data.dto.MessageResponse
import com.fpclient.android.data.dto.StartEmailChangeRequest
import com.fpclient.android.data.dto.UserDto
import com.fpclient.android.data.dto.UserPreviewDto
import com.fpclient.android.data.dto.UserSearchResultDto
import com.fpclient.android.data.dto.UserUpdateRequest
import com.fpclient.android.data.dto.VerifyEmailChangeRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.ErrorMessages
import com.fpclient.android.data.network.FitPubApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class UserRepository(
    private val api: FitPubApi,
) {

    suspend fun me(): ApiResult<UserDto> {
        return try {
            val response = api.me()
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty user response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun profile(username: String): ApiResult<UserDto> {
        return try {
            val response = api.userProfile(username.trim().removePrefix("@"))
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty profile response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /** Resolves a possibly federated handle (`@user@host`) to an [ActorDto] via the server's WebFinger discovery. */
    suspend fun discoverRemote(handle: String): ApiResult<ActorDto> {
        return try {
            val response = api.discoverRemote(handle.trim())
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty discover response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun updateMe(request: UserUpdateRequest): ApiResult<UserDto> {
        return try {
            val response = api.updateMe(request)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty update response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> {
        return try {
            val response = api.changePassword(ChangePasswordRequest(currentPassword = current, newPassword = new))
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /**
     * Searches users, federation-aware. Plain queries match local users; handles of the
     * form `@user@host` or `user@host` trigger a remote-inclusive lookup. Since server
     * implementations differ on whether they match the full handle or just the local
     * part, both variants are queried and the results merged and de-duplicated.
     */
    suspend fun search(query: String): ApiResult<UserSearchResultDto> {
        val cleaned = query.trim().removePrefix("@")
        if (cleaned.isBlank()) return ApiResult.Success(UserSearchResultDto())
        return try {
            val hasRemoteHost = cleaned.substringAfter('@', missingDelimiterValue = "").isNotBlank()
            if (!hasRemoteHost) {
                executeSearch(cleaned)
            } else {
                val byHandle = runCatching { api.searchUsers(cleaned, includeRemote = true) }.getOrNull()
                val byName = runCatching { api.searchUsers(cleaned.substringBefore('@'), includeRemote = true) }.getOrNull()
                val successes = listOfNotNull(byHandle, byName)
                    .filter { it.isSuccessful }
                    .mapNotNull { it.body() }
                if (successes.isEmpty()) {
                    val failure = byHandle ?: byName
                    return ApiResult.Error(
                        ErrorMessages.extract(failure?.errorBody()?.string()),
                        failure?.code() ?: 0,
                    )
                }
                val seen = mutableSetOf<String>()
                val merged = successes.flatMap { it.content }.filter { user ->
                    val key = user.id ?: user.username ?: return@filter false
                    seen.add(key)
                }
                ApiResult.Success(UserSearchResultDto(content = merged))
            }
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    private suspend fun executeSearch(cleaned: String): ApiResult<UserSearchResultDto> {
        val response = api.searchUsers(cleaned)
        if (!response.isSuccessful) {
            return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        }
        return ApiResult.Success(response.body() ?: UserSearchResultDto())
    }

    suspend fun browse(): ApiResult<UserSearchResultDto> {
        return try {
            val response = api.browseUsers()
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: UserSearchResultDto())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun followStatus(username: String): ApiResult<FollowStatusDto> {
        return try {
            val response = api.followStatus(username)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: FollowStatusDto())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun follow(username: String): ApiResult<Unit> {
        return try {
            val response = api.follow(username)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun unfollow(username: String): ApiResult<Unit> {
        return try {
            val response = api.unfollow(username)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun acceptFollowRequest(username: String): ApiResult<Unit> {
        return try {
            val response = api.acceptFollowRequest(username)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun rejectFollowRequest(username: String): ApiResult<Unit> {
        return try {
            val response = api.rejectFollowRequest(username)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun emailChangeStatus(): ApiResult<EmailChangeStatusResponse> {
        return try {
            val response = api.emailChangeStatus()
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: EmailChangeStatusResponse())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /**
     * The four calls of the e-mail change flow. `start` answers **202**; the rest answer 200
     * with a `MessageResponse` (or 204 for cancel). Errors keep the server's own wording —
     * "That address is already in use", "Verification code is invalid or expired" — because
     * each one tells the user what to do next.
     */
    suspend fun startEmailChange(newEmail: String): ApiResult<MessageResponse> =
        messageCall { api.startEmailChange(StartEmailChangeRequest(newEmail.trim())) }

    suspend fun verifyEmailChange(code: String): ApiResult<MessageResponse> =
        messageCall { api.verifyEmailChange(VerifyEmailChangeRequest(code.trim())) }

    suspend fun resendEmailChange(): ApiResult<MessageResponse> =
        messageCall { api.resendEmailChange() }

    suspend fun cancelEmailChange(): ApiResult<Unit> {
        return try {
            val response = api.cancelEmailChange()
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /**
     * Minimal card for a restricted profile. The profile endpoint answers 403 for those
     * accounts, so this is the only way to learn their display name and avatar — the server
     * grants it deliberately, "without full profile access".
     */
    suspend fun preview(username: String): ApiResult<UserPreviewDto> {
        return try {
            val response = api.userPreview(username.trim().removePrefix("@"))
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: UserPreviewDto())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /**
     * The signed-in user's Gravatar picture as raw bytes.
     *
     * Returned as bytes rather than a decoded image so the screen can hold it in state and
     * render it however it likes; the server answers with a default avatar when no Gravatar
     * exists, so a non-null result is not a promise that a real Gravatar was found.
     */
    suspend fun gravatarPreview(): ApiResult<ByteArray> {
        return try {
            val response = api.gravatarPreview()
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            val bytes = response.body()?.bytes()
            if (bytes == null || bytes.isEmpty()) {
                ApiResult.Error("The server sent an empty image.")
            } else {
                ApiResult.Success(bytes)
            }
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /** Shared plumbing for the `MessageResponse` calls; [EmailChangeStatusResponse] is separate. */
    private suspend fun messageCall(call: suspend () -> retrofit2.Response<MessageResponse>):
        ApiResult<MessageResponse> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                ApiResult.Success(response.body() ?: MessageResponse())
            } else {
                ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun followers(username: String, page: Int = 0, size: Int = 50): ApiResult<List<UserDto>> {
        return try {
            val response = api.followers(username.trim().removePrefix("@"), page = page, size = size)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: emptyList())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun following(username: String, page: Int = 0, size: Int = 50): ApiResult<List<UserDto>> {
        return try {
            val response = api.following(username.trim().removePrefix("@"), page = page, size = size)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: emptyList())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun uploadAvatar(context: Context, uri: Uri): ApiResult<UserDto> {
        return try {
            val file = copyUriToCache(context, uri) ?: return ApiResult.Error("Could not read the image")
            val part = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody("image/*".toMediaTypeOrNull()),
            )
            val response = api.uploadAvatar(part)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty avatar response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun deleteAvatar(): ApiResult<Unit> {
        return try {
            val response = api.deleteAvatar()
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /**
     * The signed-in user's activity heatmap.
     *
     * The server has exactly one heatmap route — `GET /api/web/heatmap/me` — so this takes no
     * username. It used to branch to a `heatmap/user/{username}` call that does not exist on
     * the server, which meant the heatmap request failed on *every* profile, the signed-in
     * user's included, and the card silently never appeared.
     */
    suspend fun heatmap(): ApiResult<HeatmapResponse> {
        return try {
            val response = api.myHeatmap()
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            // The wire format is a GeoJSON FeatureCollection; the card draws the UI model.
            val body = response.body() ?: return ApiResult.Success(HeatmapResponse())
            ApiResult.Success(HeatmapMapper.toUi(body))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun uploadProfileHeader(context: Context, uri: Uri): ApiResult<UserDto> {
        return try {
            val file = copyUriToCache(context, uri) ?: return ApiResult.Error("Could not read the image")
            val part = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody("image/*".toMediaTypeOrNull()),
            )
            val response = api.uploadProfileHeader(part)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty profile-header response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun deleteProfileHeader(): ApiResult<Unit> {
        return try {
            val response = api.deleteProfileHeader()
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun deleteAccount(): ApiResult<Unit> {
        return try {
            val response = api.deleteAccount()
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /** IANA time-zone identifiers accepted by UserUpdateRequest.timezone. */
    suspend fun timezones(): ApiResult<List<String>> {
        return try {
            val response = api.timezones()
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: emptyList())
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error", throwable = e)
        }
    }

    private suspend fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "avatar_${System.currentTimeMillis()}"
            val file = File(context.cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file
        } catch (_: Exception) {
            null
        }
    }
}
