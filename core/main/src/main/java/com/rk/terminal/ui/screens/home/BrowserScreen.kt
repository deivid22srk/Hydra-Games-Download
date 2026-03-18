package com.rk.terminal.ui.screens.home

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.rk.terminal.ui.activities.terminal.MainActivity
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(url: String, mainActivity: MainActivity, navController: NavController) {
    val decodedUrl = URLDecoder.decode(url, "UTF-8")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navegador") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }
                    }
                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
                        triggerAria2Download(downloadUrl, mainActivity, "Download do Navegador")
                        mainActivity.runOnUiThread {
                            navController.popBackStack()
                        }
                    }
                    loadUrl(decodedUrl)
                }
            }
        )
    }
}
