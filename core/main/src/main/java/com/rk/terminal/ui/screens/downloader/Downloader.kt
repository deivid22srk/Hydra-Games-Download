package com.rk.terminal.ui.screens.downloader

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.rk.libcommons.*
import com.rk.resources.strings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.rk.terminal.ui.screens.terminal.MkSession
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.terminal.ui.screens.terminal.TerminalBackEnd
import com.rk.terminal.ui.screens.terminal.TerminalScreen
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.UnknownHostException

@Composable
fun Downloader(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    val installingStr = stringResource(strings.installing)
    val networkErrorStr = stringResource(strings.network_error)
    val setupFailedStr = stringResource(strings.setup_failed)
    var progressText by remember { mutableStateOf(installingStr) }
    var isSetupComplete by remember { mutableStateOf(false) }
    var needsDownload by remember { mutableStateOf(false) }
    var terminalSession by remember { mutableStateOf<TerminalSession?>(null) }
    var isInstalling by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Rootfs.isFullyInstalled()) {
            navController.navigate(MainActivityRoutes.Home.route) {
                popUpTo(MainActivityRoutes.MainScreen.route) { inclusive = true }
            }
            return@LaunchedEffect
        }

        try {
            val abi = Build.SUPPORTED_ABIS.firstOrNull {
                it in abiMap
            } ?: throw RuntimeException("Unsupported CPU")

            val filesToDownload = listOf(
                "libtalloc.so.2" to abiMap[abi]!!.talloc,
                "proot" to abiMap[abi]!!.proot,
                "alpine.tar.gz" to abiMap[abi]!!.alpine
            ).map { (name, url) -> DownloadFile(url, Rootfs.reTerminal.child(name)) }

            needsDownload = filesToDownload.any { !it.outputFile.exists() }

            setupEnvironment(
                filesToDownload,
                onProgress = { completed, total, currentProgress ->
                    if (needsDownload) {
                        progress = ((completed + currentProgress) / total).coerceIn(0f, 1f)
                        progressText = "Downloading.. ${(progress * 100).toInt()}%"
                    }
                },
                onComplete = {
                    isInstalling = true
                },
                onError = { error ->
                    toast(if (error is UnknownHostException) networkErrorStr else setupFailedStr.format(error.message))
                }
            )
        } catch (e: Exception) {
            toast(if (e is UnknownHostException) networkErrorStr else setupFailedStr.format(e.message))
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!isSetupComplete) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (needsDownload || progress < 1f) {
                    Text(progressText, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.8f))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isInstalling) {
                    Text("Installing packages...", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(300.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                TerminalView(ctx, null).apply {
                                            val backend = object : TerminalSessionClient by TerminalBackEnd(this, mainActivity) {
                                        override fun onSessionFinished(finishedSession: TerminalSession) {
                                            if (finishedSession.exitStatus == 0) {
                                                Rootfs.reTerminal.child(".installed").createNewFile()
                                                Rootfs.isFullyInstalled.value = true
                                                isSetupComplete = true
                                                mainActivity.runOnUiThread {
                                                    navController.navigate(MainActivityRoutes.Home.route) {
                                                        popUpTo(MainActivityRoutes.MainScreen.route) { inclusive = true }
                                                    }
                                                }
                                            } else {
                                                mainActivity.runOnUiThread {
                                                    toast("Installation failed with exit code ${finishedSession.exitStatus}")
                                                }
                                            }
                                        }
                                    }

                                    val session = MkSession.createSession(
                                        mainActivity,
                                        backend,
                                        "install_session",
                                        WorkingMode.ALPINE
                                    )
                                    // Override arguments to run installation and exit
                                    // The init.sh already does the apk install if packages are missing.
                                    // We just need to trigger it and then exit.

                                    terminalSession = session
                                    attachSession(session)
                                    setTerminalViewClient(TerminalBackEnd(this, mainActivity))
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private data class DownloadFile(val url: String, val outputFile: File)

private suspend fun setupEnvironment(
    filesToDownload: List<DownloadFile>,
    onProgress: (Int, Int, Float) -> Unit,
    onComplete: () -> Unit,
    onError: (Exception) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            var completedFiles = 0
            val totalFiles = filesToDownload.size

            filesToDownload.forEach { file ->
                val outputFile = file.outputFile.apply { parentFile?.mkdirs() }
                if (!outputFile.exists()) {
                    downloadFile(file.url, outputFile) { downloaded, total ->
                        runOnUiThread { onProgress(completedFiles, totalFiles, downloaded.toFloat() / total) }
                    }
                }
                completedFiles++
                runOnUiThread { onProgress(completedFiles, totalFiles, 1f) }
                outputFile.setExecutable(true, false)
            }
            runOnUiThread { onComplete() }
        } catch (e: Exception) {
            localDir().deleteRecursively()
            withContext(Dispatchers.Main) { onError(e) }
        }
    }
}

private suspend fun downloadFile(url: String, outputFile: File, onProgress: (Long, Long) -> Unit) {
    withContext(Dispatchers.IO) {
        OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download file: ${response.code}")

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            outputFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        withContext(Dispatchers.Main) { onProgress(downloadedBytes, totalBytes) }
                    }
                }
            }
        }
    }
}

private val abiMap = mapOf(
    "x86_64" to AbiUrls(
        talloc = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/libtalloc.so.2",
        proot = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/proot",
        alpine = "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/alpine-minirootfs-3.21.0-x86_64.tar.gz"
    ),
    "arm64-v8a" to AbiUrls(
        talloc = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/libtalloc.so.2",
        proot = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/proot",
        alpine = "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz"
    ),
    "armeabi-v7a" to AbiUrls(
        talloc = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/libtalloc.so.2",
        proot = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/proot",
        alpine = "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armhf/alpine-minirootfs-3.21.0-armhf.tar.gz"
    )
)

private data class AbiUrls(val talloc: String, val proot: String, val alpine: String)
