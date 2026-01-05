package com.mindapps.data

import com.google.gson.Gson
import com.mindapps.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class MindAppsApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val baseUrl: String
        get() = BuildConfig.API_BASE_URL

    private val appsEndpoint: String
        get() = BuildConfig.APPS_JSON_ENDPOINT

    private val dataEndpoint: String
        get() = BuildConfig.DATA_ENDPOINT

    private val secretKey: String
        get() = BuildConfig.SECRET_KEY

    suspend fun fetchApps(): Result<List<MindApp>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$appsEndpoint")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val appsResponse = gson.fromJson(body, MindAppsResponse::class.java)
                    Result.success(appsResponse.apps)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendDownloadAnalytics(
        packageName: String,
        appName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("secret_key", secretKey)
                .add("package_name", packageName)
                .add("app_name", appName)
                .add("action", "download")
                .add("timestamp", System.currentTimeMillis().toString())
                .build()

            val request = Request.Builder()
                .url("$baseUrl$dataEndpoint")
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        apkUrl: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(apkUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(destinationFile)

                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val buffer = ByteArray(8192)

                    inputStream.use { input ->
                        outputStream.use { output ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (contentLength > 0) {
                                    onProgress(totalBytesRead.toFloat() / contentLength)
                                }
                            }
                        }
                    }

                    onProgress(1f)
                    Result.success(destinationFile)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
