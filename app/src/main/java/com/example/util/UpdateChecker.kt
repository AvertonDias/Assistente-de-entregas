package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String = "",
    val forceUpdate: Boolean = false
)

object UpdateChecker {

    const val DEFAULT_GITHUB_API_URL = "https://api.github.com/repos/AvertonDias/Assistente-de-entregas/releases/latest"
    const val DEFAULT_RAW_VERSION_URL = "https://raw.githubusercontent.com/AvertonDias/Assistente-de-entregas/main/version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Busca atualizações automaticamente sem necessidade de URL manual.
     * Tenta primeiro o GitHub Releases oficial e depois o version.json bruto.
     */
    suspend fun checkForUpdates(
        context: Context,
        customUrl: String? = null
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val urlsToTry = if (!customUrl.isNullOrBlank()) {
            listOf(customUrl.trim())
        } else {
            listOf(DEFAULT_GITHUB_API_URL, DEFAULT_RAW_VERSION_URL)
        }

        val currentVersionCode = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            BuildConfig.VERSION_CODE
        }
        val currentVersionName = BuildConfig.VERSION_NAME

        for (url in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Assistente-Entregas-Android-Updater")
                    .header("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val jsonString = response.body?.string() ?: continue
                val json = JSONObject(jsonString)

                // Caso 1: Formato padrão de Release do GitHub API
                if (json.has("tag_name") || json.has("assets")) {
                    val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                    val releaseName = json.optString("name", tagName)
                    val body = json.optString("body", "Nova versão disponível no repositório.")
                    val assets = json.optJSONArray("assets")
                    var apkDownloadUrl = ""

                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkDownloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    // Se não tiver asset .apk explícito, tenta link direto padrão de release
                    if (apkDownloadUrl.isBlank() && tagName.isNotBlank()) {
                        apkDownloadUrl = "https://github.com/AvertonDias/Assistente-de-entregas/releases/download/v$tagName/app-release.apk"
                    }

                    if (isRemoteNewer(tagName, currentVersionName, currentVersionCode) && apkDownloadUrl.isNotBlank()) {
                        return@withContext AppUpdateInfo(
                            versionCode = extractVersionCode(tagName, currentVersionCode + 1),
                            versionName = tagName.ifBlank { releaseName },
                            apkUrl = apkDownloadUrl,
                            changelog = body,
                            forceUpdate = false
                        )
                    }
                }

                // Caso 2: Formato customizado version.json
                val remoteVersionCode = json.optInt("versionCode", 0)
                val remoteVersionName = json.optString("versionName", "")
                val apkUrl = json.optString("apkUrl", "")
                val changelog = json.optString("changelog", "Melhorias gerais e correções.")
                val forceUpdate = json.optBoolean("forceUpdate", false)

                if ((remoteVersionCode > currentVersionCode || isRemoteNewer(remoteVersionName, currentVersionName, currentVersionCode)) && apkUrl.isNotBlank()) {
                    return@withContext AppUpdateInfo(
                        versionCode = if (remoteVersionCode > 0) remoteVersionCode else currentVersionCode + 1,
                        versionName = remoteVersionName.ifBlank { "v${remoteVersionCode}" },
                        apkUrl = apkUrl,
                        changelog = changelog,
                        forceUpdate = forceUpdate
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        null
    }

    private fun isRemoteNewer(remote: String, current: String, currentCode: Int): Boolean {
        if (remote.isBlank()) return false
        val cleanRemote = remote.removePrefix("v").trim()
        val cleanCurrent = current.removePrefix("v").trim()
        
        if (cleanRemote == cleanCurrent) return false

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun extractVersionCode(tag: String, fallback: Int): Int {
        val digitsOnly = tag.filter { it.isDigit() }
        return digitsOnly.toIntOrNull() ?: fallback
    }

    /**
     * Baixa o arquivo APK e aciona a instalação.
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(apkUrl.trim())
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onError("Falha ao baixar atualização: Código ${response.code}")
                }
                return@withContext
            }

            val body = response.body
            if (body == null) {
                withContext(Dispatchers.Main) {
                    onError("Resposta vazia do servidor")
                }
                return@withContext
            }

            val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val apkFile = File(downloadsDir, "app_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                if (totalBytes > 0) {
                    val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            withContext(Dispatchers.Main) {
                onComplete()
                installApk(context, apkFile)
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Erro ao baixar atualização: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Inicia a intent de instalação de APK no Android.
     */
    fun installApk(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        context,
                        "Por favor, autorize a instalação de fontes desconhecidas para atualizar",
                        Toast.LENGTH_LONG
                    ).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao iniciar instalador: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

