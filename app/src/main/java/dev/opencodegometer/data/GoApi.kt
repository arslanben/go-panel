package dev.opencodegometer.data

import dev.opencodegometer.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object GoApi {
    const val ENDPOINT = "https://opencode.ai/console/api/go/status"

    fun fetch(apiKey: String): ApiResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "OpenCodeGoMeter/${BuildConfig.VERSION_NAME}")
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            when {
                status == 200 -> {
                    val data = GoStatusParser.parse(body)
                    if (data != null) {
                        ApiResult.Success(data)
                    } else {
                        ApiResult.Failure(ErrorCode.PARSE, status)
                    }
                }
                status == 401 || status == 403 -> ApiResult.Failure(ErrorCode.UNAUTHORIZED, status)
                status == 404 -> ApiResult.Failure(ErrorCode.NO_SUBSCRIPTION, status)
                status == 429 -> ApiResult.Failure(ErrorCode.RATE_LIMITED, status)
                status in 500..599 -> ApiResult.Failure(ErrorCode.SERVER, status)
                else -> ApiResult.Failure(ErrorCode.SERVER, status)
            }
        } catch (e: SocketTimeoutException) {
            ApiResult.Failure(ErrorCode.NETWORK)
        } catch (e: IOException) {
            ApiResult.Failure(ErrorCode.NETWORK)
        } catch (e: Exception) {
            ApiResult.Failure(ErrorCode.SERVER)
        } finally {
            connection?.disconnect()
        }
    }
}
