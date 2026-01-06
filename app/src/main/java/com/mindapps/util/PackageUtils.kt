package com.mindapps.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Utility object for package management operations.
 *
 * Supports fallback package names using the `|` separator.
 * For example: "com.app.main|com.app.alt|com.app.legacy"
 * will check each package in order and use the first one found installed.
 */
object PackageUtils {

    /**
     * Parse a package name string that may contain fallback alternatives.
     * Example: "com.app.main|com.app.alt" -> ["com.app.main", "com.app.alt"]
     */
    fun parsePackageNames(packageNameWithFallbacks: String): List<String> {
        return packageNameWithFallbacks.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Get the primary package name (first one in the list).
     */
    fun getPrimaryPackageName(packageNameWithFallbacks: String): String {
        return parsePackageNames(packageNameWithFallbacks).firstOrNull() ?: packageNameWithFallbacks
    }

    /**
     * Find the first installed package from a list of fallback package names.
     * Returns the package name that is installed, or null if none are installed.
     */
    fun findInstalledPackage(context: Context, packageNameWithFallbacks: String): String? {
        val packages = parsePackageNames(packageNameWithFallbacks)
        return packages.firstOrNull { isPackageInstalledSingle(context, it) }
    }

    /**
     * Check if a single package is installed (no fallback parsing).
     */
    private fun isPackageInstalledSingle(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Check if any of the package variants is installed.
     * Supports fallback package names separated by `|`.
     */
    fun isPackageInstalled(context: Context, packageNameWithFallbacks: String): Boolean {
        return findInstalledPackage(context, packageNameWithFallbacks) != null
    }

    /**
     * Get the installed version of any matching package variant.
     * Returns the version of the first installed variant, or null if none installed.
     */
    fun getInstalledVersion(context: Context, packageNameWithFallbacks: String): String? {
        val installedPackage = findInstalledPackage(context, packageNameWithFallbacks)
        return installedPackage?.let { getVersionForPackage(context, it) }
    }

    /**
     * Get version for a specific package name (no fallback parsing).
     */
    private fun getVersionForPackage(context: Context, packageName: String): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val v1 = parts1.getOrElse(i) { 0 }
            val v2 = parts2.getOrElse(i) { 0 }
            if (v1 != v2) {
                return v1.compareTo(v2)
            }
        }
        return 0
    }

    fun isUpdateAvailable(installedVersion: String?, remoteVersion: String): Boolean {
        if (installedVersion == null) return false
        return compareVersions(remoteVersion, installedVersion) > 0
    }

    /**
     * Open the installed app. Tries each fallback package until one works.
     */
    fun openApp(context: Context, packageNameWithFallbacks: String): Boolean {
        val installedPackage = findInstalledPackage(context, packageNameWithFallbacks)
        if (installedPackage == null) return false

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(installedPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Uninstall the installed app variant.
     */
    fun uninstallApp(context: Context, packageNameWithFallbacks: String) {
        val installedPackage = findInstalledPackage(context, packageNameWithFallbacks)
            ?: getPrimaryPackageName(packageNameWithFallbacks)

        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$installedPackage")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
