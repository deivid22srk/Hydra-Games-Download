package com.rk.terminal.ui.screens.downloader

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.rk.libcommons.*
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.rk.terminal.ui.screens.terminal.MkSession
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.terminal.ui.screens.terminal.TerminalBackEnd
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
    val networkErrorStr = stringResource(strings.network_error)
    val setupFailedStr = stringResource(strings.setup_failed)
    var isSetupComplete by remember { mutableStateOf(false) }
    var needsDownload by remember { mutableStateOf(false) }
    var terminalSession by remember { mutableStateOf<TerminalSession?>(null) }
    var isInstalling by remember { mutableStateOf(false) }

    // 0: Downloading, 1: Extracting, 2: Installing Packages, 3: Initializing
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = remember {
        listOf(
            "Baixando arquivos",
            "Extraindo sistema",
            "Instalando pacotes base",
            "Iniciando serviços"
        )
    }

    LaunchedEffect(Unit) {
        if (Rootfs.isFullyInstalled()) {
            if (Settings.isExtraSetupComplete) {
                navController.navigate(MainActivityRoutes.MainScreen.route) {
                    popUpTo(MainActivityRoutes.MainScreen.route) { inclusive = true }
                }
            } else {
                navController.navigate(MainActivityRoutes.SetupExtra.route) {
                    popUpTo(MainActivityRoutes.MainScreen.route) { inclusive = true }
                }
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

            if (!needsDownload) {
                currentStep = 1
            }

            setupEnvironment(
                filesToDownload,
                onProgress = { completed, total, currentProgress ->
                    if (needsDownload) {
                        progress = ((completed + currentProgress) / total).coerceIn(0f, 1f)
                    }
                },
                onComplete = {
                    isInstalling = true
                    if (currentStep == 0) {
                        currentStep = 1
                    }
                },
                onError = { error ->
                    toast(if (error is UnknownHostException) networkErrorStr else setupFailedStr.format(error.message))
                }
            )
        } catch (e: Exception) {
            toast(if (e is UnknownHostException) networkErrorStr else setupFailedStr.format(e.message))
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        if (!isSetupComplete) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Circular Progress
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(
                        progress = { if (currentStep == 0) progress else 1f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    if (currentStep > 0) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Steps List
                Column(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEachIndexed { index, stepTitle ->
                        StepItem(
                            title = "${index + 1}. $stepTitle",
                            isActive = currentStep == index,
                            isCompleted = currentStep > index
                        )
                    }
                }

                if (isInstalling) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                TerminalView(ctx, null).apply {
                                    setTextSize(dpToPx(Settings.terminal_font_size.toFloat(), ctx))
                                    val backend = object : TerminalSessionClient by TerminalBackEnd(this, mainActivity) {
                                        override fun onTextChanged(changedSession: TerminalSession) {
                                            val text = changedSession.emulator.screen.getSelectedText(0, 0, changedSession.emulator.mColumns, changedSession.emulator.mRows)
                                            if (text.contains("Extracting alpine rootfs")) {
                                                currentStep = 1
                                            } else if (text.contains("Installing Important packages")) {
                                                currentStep = 2
                                            } else if (text.contains("Successfully Installed") && currentStep == 2) {
                                                currentStep = 3
                                            }
                                            onScreenUpdated()
                                        }

                                        override fun onSessionFinished(finishedSession: TerminalSession) {
                                            if (finishedSession.exitStatus == 0) {
                                                Rootfs.reTerminal.child(".installed").createNewFile()
                                                Rootfs.isFullyInstalled.value = true
                                                isSetupComplete = true
                                                mainActivity.runOnUiThread {
                                                    if (Settings.isExtraSetupComplete) {
                                                        navController.navigate(MainActivityRoutes.MainScreen.route) {
                                                            popUpTo(MainActivityRoutes.MainScreen.route) { inclusive = true }
                                                        }
                                                    } else {
                                                        navController.navigate(MainActivityRoutes.SetupExtra.route) {
                                                            popUpTo(MainActivityRoutes.MainScreen.route) { inclusive = true }
                                                        }
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

@Composable
fun StepItem(title: String, isActive: Boolean, isCompleted: Boolean) {
    val color by animateColorAsState(
        targetValue = when {
            isCompleted -> MaterialTheme.colorScheme.primary
            isActive -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        label = "color"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
        if (isActive && !isCompleted) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = color
            )
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
