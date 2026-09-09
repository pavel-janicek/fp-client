package com.fpclient.android.data.repository

import android.content.Context
import android.net.Uri
import com.fpclient.android.data.dto.ActivityDto
import com.fpclient.android.data.dto.ActivityUpdateRequest
import com.fpclient.android.data.dto.BoostDto
import com.fpclient.android.data.dto.CommentCreateRequest
import com.fpclient.android.data.dto.CommentDto
import com.fpclient.android.data.dto.LikeDto
import com.fpclient.android.data.dto.LocationSuggestionDto
import com.fpclient.android.data.dto.ManualActivityRequest
import com.fpclient.android.data.dto.PageEnvelopeActivitySummaryDto
import com.fpclient.android.data.dto.PageEnvelopeCommentDto
import com.fpclient.android.data.dto.ReactionRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.ErrorMessages
import com.fpclient.android.data.network.FitPubApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ActivityRepository(
    private val api: FitPubApi,
) {

    suspend fun detail(id: String): ApiResult<ActivityDto> {
        return try {
            val response = api.getActivity(id)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty activity response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun update(id: String, request: ActivityUpdateRequest): ApiResult<ActivityDto> {
        return try {
            val response = api.updateActivity(id, request)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty update response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun delete(id: String): ApiResult<Unit> {
        return try {
            val response = api.deleteActivity(id)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun userActivities(username: String, page: Int, size: Int): ApiResult<PageEnvelopeActivitySummaryDto> {
        return try {
            val response = api.userActivities(username, page, size)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: PageEnvelopeActivitySummaryDto())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun createManual(request: ManualActivityRequest): ApiResult<ActivityDto> {
        return try {
            val response = api.createManualActivity(request)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty manual activity response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /** Uploads a FIT/GPX/TCX file picked through the system file picker. */
    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        title: String?,
        description: String?,
        visibility: String?,
    ): ApiResult<ActivityDto> {
        return try {
            val file = copyUriToCache(context, uri) ?: return ApiResult.Error("Could not read the selected file")
            val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: file.name
            val mediaType = when (file.extension.lowercase()) {
                "fit" -> "application/octet-stream"
                "gpx" -> "application/gpx+xml"
                "tcx" -> "application/vnd.garmin.tcx+xml"
                else -> "application/octet-stream"
            }.toMediaTypeOrNull()
            val part = MultipartBody.Part.createFormData("file", name, file.asRequestBody(mediaType))
            val titleBody = title?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
            val descBody = description?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
            val visibilityBody = visibility?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadActivity(part, titleBody, descBody, visibilityBody)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty upload response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun locationSuggestions(query: String): ApiResult<List<LocationSuggestionDto>> {
        return try {
            val response = api.locationSuggestions(query)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: emptyList())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    // --- Comments ---

    suspend fun comments(activityId: String, page: Int, size: Int): ApiResult<PageEnvelopeCommentDto> {
        return try {
            val response = api.comments(activityId, page, size)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: PageEnvelopeCommentDto())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun addComment(activityId: String, content: String): ApiResult<CommentDto> {
        return try {
            val response = api.addComment(activityId, CommentCreateRequest(content))
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty comment response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun deleteComment(activityId: String, commentId: String): ApiResult<Unit> {
        return try {
            val response = api.deleteComment(activityId, commentId)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    // --- Reactions / likes ---

    suspend fun likes(activityId: String): ApiResult<List<LikeDto>> {
        return try {
            val response = api.likes(activityId)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: emptyList())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun react(activityId: String, emoji: String?): ApiResult<LikeDto> {
        return try {
            val response = api.react(activityId, ReactionRequest(emoji))
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty reaction response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun unreact(activityId: String): ApiResult<Unit> {
        return try {
            val response = api.unreact(activityId)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    // --- Boosts ---

    suspend fun boosts(activityId: String): ApiResult<List<BoostDto>> {
        return try {
            val response = api.boosts(activityId)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: emptyList())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun boost(activityId: String): ApiResult<BoostDto> {
        return try {
            val response = api.boost(activityId)
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty boost response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun unboost(activityId: String): ApiResult<Unit> {
        return try {
            val response = api.unboost(activityId)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    private suspend fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            val resolver = context.contentResolver
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "activity_upload"
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$name")
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun visibilityFromName(name: String?): String = name ?: "PUBLIC"
    }
}
