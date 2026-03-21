package com.rk.terminal.ui.activities.terminal

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.rk.terminal.service.SessionService
import com.rk.settings.Settings
import com.rk.terminal.ui.navHosts.MainActivityNavHost
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.home.HydraApi
import com.rk.terminal.ui.screens.home.HydraProfile
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.rk.terminal.ui.screens.terminal.MkSession
import com.rk.terminal.ui.screens.terminal.TerminalBackEnd
import com.rk.terminal.ui.screens.terminal.TerminalScreen
import com.rk.terminal.ui.screens.terminal.terminalView
import com.rk.terminal.ui.theme.KarbonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File

class MainActivity : ComponentActivity() {
    var sessionBinder:SessionService.SessionBinder? = null
    var isBound = false


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SessionService.SessionBinder
            sessionBinder = binder
            isBound = true

            // Start Aria2 if not running. Use a persistent check to avoid multi-spawn.
            lifecycleScope.launch(Dispatchers.Main) {
                val service = sessionBinder?.getService()
                if (service != null && !service.sessionList.containsKey("aria2_daemon")) {
                    val dummyView = com.termux.view.TerminalView(this@MainActivity, null)
                    val client = TerminalBackEnd(dummyView, this@MainActivity)
                    sessionBinder?.createSession(
                        "aria2_daemon",
                        client,
                        this@MainActivity,
                        WorkingMode.ALPINE,
                        initialArgs = listOf("sh", "-c", "aria2c --daemon=false --enable-rpc=true --rpc-listen-all=false --rpc-listen-port=${Settings.aria2RpcPort} --async-dns=false")
                    )
                }
            }

            lifecycleScope.launch(Dispatchers.Main){
                setContent {
                    KarbonTheme {
                        Surface {
                            val navController = rememberNavController()
                            MainActivityNavHost(navController = navController, mainActivity = this@MainActivity)

                            val backStackEntry by navController.currentBackStackEntryAsState()

                            val focusManager = LocalFocusManager.current
                            val keyboardController = LocalSoftwareKeyboardController.current

                            LaunchedEffect(backStackEntry?.destination?.route) {
                                if (backStackEntry?.destination?.route != MainActivityRoutes.MainScreen.route) {
                                    // 1️⃣ Clear Compose focus
                                    focusManager.clearFocus(force = true)

                                    // 2️⃣ Clear Android View focus
                                    terminalView.get()?.clearFocus()

                                    // 3️⃣ Hide IME explicitly
                                    keyboardController?.hide()
                                }
                            }


                        }
                    }
                }
            }


        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            sessionBinder = null
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, SessionService::class.java))
        }else{
            startService(Intent(this, SessionService::class.java))
        }
        Intent(this, SessionService::class.java).also { intent ->
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }


    private var denied = 1
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted && denied <= 2) {
                denied++
                requestPermission()
            }
        }

    fun requestPermission(){
        // Only request on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var isKeyboardVisible = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermission()

        if (intent.hasExtra("awake_intent")){
            moveTaskToBack(true)
        }
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "hydralauncher") return

        when (data.host) {
            "auth" -> {
                val payload = data.getQueryParameter("payload")
                if (payload != null) {
                    try {
                        val decoded = android.util.Base64.decode(payload, android.util.Base64.DEFAULT).decodeToString()
                        val gson = com.google.gson.Gson()
                        val authData = gson.fromJson(decoded, Map::class.java)

                        val accessToken = authData["accessToken"] as? String
                        val refreshToken = authData["refreshToken"] as? String
                        val expiresIn = (authData["expiresIn"] as? Double)?.toLong() ?: 0L
                        val userId = authData["userId"] as? String

                        if (accessToken != null && refreshToken != null) {
                            Settings.accessToken = accessToken
                            Settings.refreshToken = refreshToken
                            Settings.tokenExpiration = System.currentTimeMillis() + (expiresIn * 1000)
                            if (userId != null) {
                                Settings.userId = userId
                            }

                            // Fetch profile data
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val client = HydraApi.getClient()
                                    val request = Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/profile/me").build()
                                    client.newCall(request).execute().use { response ->
                                        if (response.isSuccessful) {
                                            val body = response.body?.string()
                                            val profile = Gson().fromJson(body, HydraProfile::class.java)
                                            profile?.let { Settings.updateFromProfile(it) }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            android.widget.Toast.makeText(this, "Login realizado com sucesso!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            "install-source" -> {
                val url = data.getQueryParameter("url")
                if (url != null) {
                    val currentSources = Settings.hydraSources.toMutableList()
                    if (currentSources.none { it.url == url }) {
                        currentSources.add(com.rk.terminal.ui.screens.home.HydraSourceConfig(url))
                        Settings.hydraSources = currentSources
                        android.widget.Toast.makeText(this, "Fonte adicionada: $url", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(this, "Fonte já existente.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    var wasKeyboardOpen = false
    override fun onPause() {
        super.onPause()
        wasKeyboardOpen = isKeyboardVisible
    }

    override fun onResume() {
        super.onResume()

        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val isVisible = keypadHeight > screenHeight * 0.15

            isKeyboardVisible = isVisible
        }


        if (wasKeyboardOpen && !isKeyboardVisible){
            terminalView.get()?.let {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
}