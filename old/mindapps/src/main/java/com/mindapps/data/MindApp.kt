package com.mindapps.data

import com.google.gson.annotations.SerializedName

data class MindApp(
    @SerializedName("icon")
    val icon: String,

    @SerializedName("version")
    val version: String,

    @SerializedName("apk_link")
    val apkLink: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("package_name")
    val packageName: String,

    @SerializedName("author")
    val author: String
)

data class MindAppsResponse(
    @SerializedName("apps")
    val apps: List<MindApp>
)

enum class AppState {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE,
    DOWNLOADING
}

data class MindAppWithState(
    val app: MindApp,
    val state: AppState,
    val installedVersion: String? = null,
    val downloadProgress: Float = 0f
)
