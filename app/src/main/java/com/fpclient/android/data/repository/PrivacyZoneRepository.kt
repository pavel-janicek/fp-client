package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.PrivacyZoneCreateRequest
import com.fpclient.android.data.dto.PrivacyZoneDto
import com.fpclient.android.data.dto.PrivacyZoneToggleRequest
import com.fpclient.android.data.dto.PrivacyZoneUpdateRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.ErrorMessages
import com.fpclient.android.data.network.FitPubApi

class PrivacyZoneRepository(
    private val api: FitPubApi,
) {

    suspend fun list(): ApiResult<List<PrivacyZoneDto>> {
        return try {
            val response = api.privacyZones()
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: emptyList())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun create(name: String, lat: Double, lon: Double, radiusMeters: Int): ApiResult<PrivacyZoneDto> {
        return try {
            val response = api.createPrivacyZone(
                PrivacyZoneCreateRequest(name = name, latitude = lat, longitude = lon, radiusMeters = radiusMeters),
            )
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty privacy zone response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun update(
        id: String,
        name: String,
        lat: Double,
        lon: Double,
        radiusMeters: Int,
    ): ApiResult<PrivacyZoneDto> {
        return try {
            val response = api.updatePrivacyZone(
                id,
                // The server's UpdatePrivacyZoneRequest declares name, latitude, longitude
                // and radiusMeters @NotNull — a partial body is rejected with 400.
                PrivacyZoneUpdateRequest(name = name, latitude = lat, longitude = lon, radiusMeters = radiusMeters),
            )
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty privacy zone response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun toggle(id: String, isActive: Boolean): ApiResult<PrivacyZoneDto> {
        return try {
            // The server's toggle endpoint is not a flip: it requires the desired state in the
            // body (`{"isActive": …}`) and rejects a bodyless PATCH with
            // 400 "Required request body is missing".
            val response = api.togglePrivacyZone(id, PrivacyZoneToggleRequest(isActive = isActive))
            if (!response.isSuccessful) {
                return ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
            ApiResult.Success(response.body() ?: error("Empty privacy zone response"))
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    suspend fun delete(id: String): ApiResult<Unit> {
        return try {
            val response = api.deletePrivacyZone(id)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }
}