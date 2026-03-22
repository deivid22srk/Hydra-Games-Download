package com.rk.terminal.ui.screens.home

import android.net.Uri
import android.os.Message
import android.webkit.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.rk.terminal.ui.activities.terminal.MainActivity
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.*

class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val webView: WebView
) {
    var title by mutableStateOf("Nova Guia")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(url: String, mainActivity: MainActivity, navController: NavController) {
    val context = LocalContext.current
    val decodedUrl = remember { URLDecoder.decode(url, "UTF-8") }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val tabs = remember { mutableStateListOf<BrowserTab>() }
    var activeTabId by remember { mutableStateOf<String?>(null) }

    var showDownloadDialog by remember { mutableStateOf<Map<String, String>?>(null) }
    var showRedirectDialog by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var redirectResult by remember { mutableStateOf<Message?>(null) }

    val downloadExtensions = remember {
        listOf(".zip", ".rar", ".exe", ".iso", ".bin", ".apk", ".7z", ".tar", ".gz", ".msi", ".dmg", ".pkg")
    }

    fun isDownloadUrl(url: String?): Boolean {
        if (url == null) return false
        val lowerUrl = url.lowercase()
        return downloadExtensions.any { lowerUrl.contains(it) } || lowerUrl.contains("download") || lowerUrl.contains("filename=")
    }

    fun createWebView(initialUrl: String?): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    tabs.find { it.webView == view }?.title = view?.title ?: "Sem título"
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val newUrl = request?.url?.toString() ?: return false

                    val currentUrl = view?.url ?: ""
                    if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                        val newHost = request.url.host
                        val currentHost = try { Uri.parse(currentUrl).host } catch (e: Exception) { null }
                        if (newHost != null && currentHost != null && newHost != currentHost) {
                            showRedirectDialog = "O site está tentando redirecionar para um domínio diferente: $newHost. Deseja prosseguir?" to {
                                view?.loadUrl(newUrl)
                            }
                            return true
                        }
                    }
                    return false
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                    redirectResult = resultMsg
                    showRedirectDialog = "O site está tentando abrir uma nova guia/janela. Deseja permitir?" to {
                        val newWv = createWebView(null)
                        val transport = redirectResult?.obj as? WebView.WebViewTransport
                        transport?.webView = newWv
                        redirectResult?.sendToTarget()

                        val newTab = BrowserTab(webView = newWv)
                        tabs.add(newTab)
                        activeTabId = newTab.id
                    }
                    return true
                }
            }
            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                showDownloadDialog = mapOf(
                    "url" to downloadUrl,
                    "userAgent" to userAgent,
                    "cookies" to (cookies ?: ""),
                    "referer" to (url ?: "")
                )
            }
            if (initialUrl != null) {
                loadUrl(initialUrl)
            }
        }
    }

    // Initialize first tab
    LaunchedEffect(Unit) {
        if (tabs.isEmpty()) {
            val wv = createWebView(decodedUrl)
            val tab = BrowserTab(webView = wv)
            tabs.add(tab)
            activeTabId = tab.id
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tabs.forEach { it.webView.destroy() }
            tabs.clear()
        }
    }

    if (showDownloadDialog != null) {
        val downloadData = showDownloadDialog!!
        AlertDialog(
            onDismissRequest = { showDownloadDialog = null },
            title = { Text("Confirmar Download") },
            text = { Text("Deseja baixar este arquivo via Aria2?\n\nURL: ${downloadData["url"]}") },
            confirmButton = {
                Button(onClick = {
                    triggerAria2Download(
                        url = downloadData["url"]!!,
                        activity = mainActivity,
                        title = "Download do Navegador",
                        userAgent = downloadData["userAgent"],
                        cookies = downloadData["cookies"],
                        referer = downloadData["referer"]
                    )
                    showDownloadDialog = null
                    navController.popBackStack()
                }) {
                    Text("Baixar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showRedirectDialog != null) {
        AlertDialog(
            onDismissRequest = {
                showRedirectDialog = null
            },
            title = { Text("Aviso do Navegador") },
            text = { Text(showRedirectDialog!!.first) },
            confirmButton = {
                Button(onClick = {
                    showRedirectDialog!!.second.invoke()
                    showRedirectDialog = null
                }) {
                    Text("Sim/Abrir")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    redirectResult = null
                    showRedirectDialog = null
                }) {
                    Text("Não/Recusar")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Guias Abertas", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = {
                            val wv = createWebView("https://www.google.com")
                            val tab = BrowserTab(webView = wv)
                            tabs.add(tab)
                            activeTabId = tab.id
                            scope.launch { drawerState.close() }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Nova Guia")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn {
                        items(tabs) { tab ->
                            val isActive = tab.id == activeTabId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeTabId = tab.id
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(vertical = 4.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tab.title,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                    IconButton(onClick = {
                                        if (isActive) {
                                            if (tabs.size > 1) {
                                                val index = tabs.indexOf(tab)
                                                activeTabId = if (index > 0) tabs[index - 1].id else tabs[index + 1].id
                                            } else {
                                                navController.popBackStack()
                                            }
                                        }
                                        tab.webView.destroy()
                                        tabs.remove(tab)
                                        if (tabs.isEmpty()) {
                                            navController.popBackStack()
                                        }
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Fechar Guia", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val currentTab = tabs.find { it.id == activeTabId }
                        Text(currentTab?.title ?: "Navegador", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (drawerState.isClosed) {
                                scope.launch { drawerState.open() }
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(if (drawerState.isClosed) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        val currentTab = tabs.find { it.id == activeTabId }
                        val currentWv = currentTab?.webView

                        IconButton(onClick = {
                            currentWv?.let { wv ->
                                val downloadUrl = wv.url ?: return@let
                                val userAgent = wv.settings.userAgentString
                                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                                showDownloadDialog = mapOf(
                                    "url" to downloadUrl,
                                    "userAgent" to userAgent,
                                    "cookies" to (cookies ?: ""),
                                    "referer" to (wv.url ?: "")
                                )
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Baixar com Aria2")
                        }

                        if (currentWv?.canGoBack() == true) {
                            IconButton(onClick = { currentWv.goBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                tabs.forEach { tab ->
                    // Only the active WebView is visible and composed
                    if (tab.id == activeTabId) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { tab.webView },
                            update = { /* The webView is already managed */ }
                        )
                    }
                }
            }
        }
    }
}
