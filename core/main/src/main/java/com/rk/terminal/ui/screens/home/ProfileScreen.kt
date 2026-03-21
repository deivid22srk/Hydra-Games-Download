package com.rk.terminal.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rk.terminal.ui.routes.MainActivityRoutes
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, userIdArg: String? = null) {
    val userId = if (userIdArg.isNullOrBlank()) null else userIdArg
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isMe = userId == null || (Settings.userId.isNotBlank() && userId == Settings.userId)

    var profile by remember {
        mutableStateOf<HydraProfile?>(
            if (isMe && Settings.userDisplayName.isNotBlank()) {
                HydraProfile(
                    id = Settings.userId,
                    displayName = Settings.userDisplayName,
                    profileImageUrl = Settings.userProfileImageUrl,
                    backgroundImageUrl = Settings.userBackgroundImageUrl,
                    bio = Settings.userBio
                )
            } else null
        )
    }
    var globalBadges by remember { mutableStateOf<List<HydraBadge>>(emptyList()) }
    var friendRequests by remember { mutableStateOf<HydraFriendRequestsResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    var editDisplayName by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var friendCodeToAdd by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("hate") }
    var reportDescription by remember { mutableStateOf("") }

    var isLoggedIn by remember { mutableStateOf(Settings.accessToken.isNotBlank()) }
    var tokenRefreshIndicator by remember { mutableStateOf(false) }
    
    // Token refresh and session management
    LaunchedEffect(Unit) {
        while(true) {
            val currentLoginState = Settings.accessToken.isNotBlank()
            if (isLoggedIn != currentLoginState) {
                isLoggedIn = currentLoginState
            }
            
            // Auto-refresh token if expiring soon (within 10 minutes)
            if (isLoggedIn && Settings.refreshToken.isNotBlank()) {
                val timeUntilExpiration = Settings.tokenExpiration - System.currentTimeMillis()
                if (timeUntilExpiration > 0 && timeUntilExpiration < 600000) { // 10 minutes
                    tokenRefreshIndicator = true
                    withContext(Dispatchers.IO) {
                        try {
                            val client = HydraApi.getClient()
                            val gson = Gson()
                            val requestBody = mapOf("refreshToken" to Settings.refreshToken)
                            val json = gson.toJson(requestBody)
                            val body = json.toRequestBody("application/json".toMediaTypeOrNull())

                            val request = Request.Builder()
                                .url("https://hydra-api-us-east-1.losbroxas.org/auth/refresh")
                                .post(body)
                                .build()

                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val respBody = response.body?.string()
                                    val data = gson.fromJson(respBody, Map::class.java)
                                    val newAccessToken = data["accessToken"] as? String
                                    val newRefreshToken = data["refreshToken"] as? String
                                    val expiresIn = (data["expiresIn"] as? Number)?.toLong() ?: 0L

                                    if (newAccessToken != null && newRefreshToken != null) {
                                        Settings.accessToken = newAccessToken
                                        Settings.refreshToken = newRefreshToken
                                        Settings.tokenExpiration = System.currentTimeMillis() + (expiresIn * 1000)
                                        android.util.Log.d("ProfileScreen", "Token refreshed successfully. New expiration: ${Settings.tokenExpiration}")
                                    }
                                } else {
                                    android.util.Log.e("ProfileScreen", "Token refresh failed: ${response.code}")
                                    if (response.code == 401) {
                                        // Token refresh failed completely - force logout
                                        Settings.accessToken = ""
                                        Settings.refreshToken = ""
                                        Settings.userId = ""
                                        Settings.tokenExpiration = 0L
                                        withContext(Dispatchers.Main) {
                                            isLoggedIn = false
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileScreen", "Token refresh error", e)
                        } finally {
                            withContext(Dispatchers.Main) {
                                tokenRefreshIndicator = false
                            }
                        }
                    }
                }
            }
            
            delay(30000) // Check every 30 seconds
        }
    }
    

    val profileImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageUpload(it, true, context, scope) { profile = it } }
    }

    val backgroundImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageUpload(it, false, context, scope) { profile = it } }
    }

    val refreshProfile = {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val gson = Gson()
                
                // Fetch Global Badges
                val badgesRequest = Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/badges").build()
                client.newCall(badgesRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val type = object : com.google.gson.reflect.TypeToken<List<HydraBadge>>() {}.type
                        globalBadges = gson.fromJson(response.body?.string(), type) ?: emptyList()
                    }
                }

                val url = if (userId == null) {
                    "https://hydra-api-us-east-1.losbroxas.org/profile/me"
                } else {
                    "https://hydra-api-us-east-1.losbroxas.org/users/$userId"
                }
                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val newProfile = gson.fromJson(body, HydraProfile::class.java)
                        profile = newProfile
                        if (isMe) {
                            Settings.updateFromProfile(newProfile)
                        }
                    } else if (response.code == 401) {
                        // Token might be invalid
                        withContext(Dispatchers.Main) {
                            isLoggedIn = false
                        }
                    }
                }
                
                // Buscar pedidos de amizade se for meu perfil
                if (isMe) {
                    val friendRequestsUrl = "https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests"
                    val friendRequestsReq = Request.Builder().url(friendRequestsUrl).build()
                    client.newCall(friendRequestsReq).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            friendRequests = gson.fromJson(body, HydraFriendRequestsResponse::class.java)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    val saveProfileChanges = {
        isLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val client = HydraApi.getClient()
                    val gson = Gson()
                    val bodyMap = mutableMapOf<String, String>()
                    if (editDisplayName != profile?.displayName) bodyMap["displayName"] = editDisplayName
                    if (editBio != profile?.bio) bodyMap["bio"] = editBio

                    if (bodyMap.isNotEmpty()) {
                        val bodyJson = gson.toJson(bodyMap)
                        val requestBody = bodyJson.toRequestBody("application/json".toMediaTypeOrNull())
                        val request = Request.Builder()
                            .url("https://hydra-api-us-east-1.losbroxas.org/profile")
                            .patch(requestBody)
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                refreshProfile()
                                withContext(Dispatchers.Main) { isEditing = false }
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) { isEditing = false }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    LaunchedEffect(userId, isLoggedIn) {
        if (isLoggedIn) {
            refreshProfile()
        } else {
            isLoading = false
            profile = null
        }
    }

    val formatPlayTime = { seconds: Long ->
        val minutes = seconds / 60
        if (minutes < 60) {
            "$minutes min"
        } else {
            val hours = minutes / 60
            "${hours}h"
        }
    }

    val addFriend = { friendCode: String ->
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val body = Gson().toJson(mapOf("friendCode" to friendCode))
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val reportUser = { reason: String, description: String ->
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val body = Gson().toJson(mapOf("reason" to reason, "description" to description))
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("https://hydra-api-us-east-1.losbroxas.org/users/${profile?.id}/report")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val blockUser = {
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val request = Request.Builder()
                    .url("https://hydra-api-us-east-1.losbroxas.org/users/${profile?.id}/block")
                    .post("".toRequestBody())
                    .build()
                client.newCall(request).execute().use { }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isMe) "Meu Perfil" else profile?.displayName ?: "Perfil", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isLoggedIn && !isLoading) {
                        if (isMe) {
                            if (isEditing) {
                                IconButton(onClick = { saveProfileChanges() }) {
                                    Icon(Icons.Default.Save, contentDescription = "Salvar")
                                }
                            } else {
                                IconButton(onClick = {
                                    editDisplayName = profile?.displayName ?: ""
                                    editBio = profile?.bio ?: ""
                                    isEditing = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }
                                IconButton(onClick = { showAddFriendDialog = true }) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = "Adicionar Amigo")
                                }
                            }
                        } else {
                            IconButton(onClick = { showReportDialog = true }) {
                                Icon(Icons.Default.Report, contentDescription = "Denunciar")
                            }
                            IconButton(onClick = { blockUser() }) {
                                Icon(Icons.Default.Block, contentDescription = "Bloquear")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoggedIn) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    // Modern Hero Banner com parallax effect
                    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                        // Background blur effect
                        AsyncImage(
                            model = profile?.backgroundImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(240.dp).blur(8.dp),
                            contentScale = ContentScale.Crop,
                            alpha = 0.4f
                        )
                        
                        // Gradient overlay for depth
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            MaterialTheme.colorScheme.surface
                                        ),
                                        startY = 0f,
                                        endY = 800f
                                    )
                                )
                        )

                        if (isEditing) {
                            FilledTonalIconButton(
                                onClick = { backgroundImageLauncher.launch("image/*") },
                                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Trocar Fundo")
                            }
                        }

                        // Modern floating avatar with glow effect
                        Surface(
                            modifier = Modifier
                                .size(130.dp)
                                .align(Alignment.BottomCenter),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(5.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            tonalElevation = 8.dp,
                            shadowElevation = 16.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                // Glow ring effect
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                    Color.Transparent
                                                )
                                            ),
                                            CircleShape
                                        )
                                )
                                
                                AsyncImage(
                                    model = profile?.profileImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                
                                if (isEditing) {
                                    IconButton(
                                        onClick = { profileImageLauncher.launch("image/*") },
                                        modifier = Modifier.fillMaxSize(),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Trocar Foto", tint = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = editDisplayName,
                            onValueChange = { editDisplayName = it },
                            label = { Text("Nome de Exibição") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = editBio,
                            onValueChange = { editBio = it },
                            label = { Text("Bio") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    } else {
                        Text(
                            text = profile?.displayName ?: "Usuário Hydra",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        profile?.id?.let { id ->
                            Surface(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(id))
                                    android.widget.Toast.makeText(context, "ID copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = id,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copiar ID",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = profile?.bio ?: "Nenhuma biografia disponível.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Modern Stats Section with animations
                        profile?.stats?.let { stats ->
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Estat\u00edsticas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(animationSpec = tween(300, delayMillis = 100))
                                ) {
                                    StatCard(
                                        icon = Icons.Default.EmojiEvents,
                                        value = "${stats.unlockedAchievementSum ?: 0}",
                                        label = "Conquistas"
                                    )
                                }
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(animationSpec = tween(300, delayMillis = 200))
                                ) {
                                    StatCard(
                                        icon = Icons.Default.History,
                                        value = formatPlayTime(stats.totalPlayTimeInSeconds?.value?.toLong() ?: 0L),
                                        label = "Tempo total"
                                    )
                                }
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(animationSpec = tween(300, delayMillis = 300))
                                ) {
                                    StatCard(
                                        icon = Icons.Default.Star,
                                        value = "${profile?.karma ?: 0}",
                                        label = "Karma"
                                    )
                                }
                            }
                        }

                        // Library Quick Access
                        if (isMe) {
                            Button(
                                onClick = { navController.navigate(MainActivityRoutes.Library.route) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Icon(Icons.Default.LibraryBooks, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("MINHA BIBLIOTECA", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Recent Games Section
                        if (!profile?.recentGames.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Atividade Recente",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                TextButton(onClick = { navController.navigate(MainActivityRoutes.Library.route) }) {
                                    Text("Ver Biblioteca", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(profile?.recentGames ?: emptyList()) { game ->
                                    ElevatedCard(
                                        modifier = Modifier.width(140.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                            AsyncImage(
                                                model = game.iconUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(game.title ?: "", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatPlayTime(game.playTimeInSeconds ?: 0L), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Badges Section
                        if (!profile?.badges.isNullOrEmpty()) {
                            Text(
                                text = "Emblemas",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp),
                                textAlign = TextAlign.Start
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(profile?.badges ?: emptyList()) { badgeName ->
                                    val badgeDef = globalBadges.find { it.name == badgeName }
                                    if (badgeDef != null) {
                                        Surface(
                                            modifier = Modifier.size(50.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            tonalElevation = 2.dp
                                        ) {
                                            AsyncImage(
                                                model = badgeDef.badge?.url,
                                                contentDescription = badgeDef.title,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Pedidos de Amizade Pendentes (apenas para meu perfil)
                        if (isMe && !friendRequests?.incoming.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Solicita\u00e7\u00f5es de Amizade",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${friendRequests?.incoming?.size}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                            
                            friendRequests?.incoming?.forEach { request ->
                                val requester = request.userA
                                if (requester != null) {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                AsyncImage(
                                                    model = requester.profileImageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp).clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = requester.displayName ?: "Usu\u00e1rio",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Pedido de amizade",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        scope.launch(Dispatchers.IO) {
                                                            try {
                                                                val client = HydraApi.getClient()
                                                                val acceptRequest = Request.Builder()
                                                                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests/${request.id}/accept")
                                                                    .patch("".toRequestBody())
                                                                    .build()
                                                                client.newCall(acceptRequest).execute().use {
                                                                    if (it.isSuccessful) {
                                                                        refreshProfile()
                                                                    }
                                                                }
                                                            } catch (e: Exception) { e.printStackTrace() }
                                                        }
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = "Aceitar")
                                                }
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        scope.launch(Dispatchers.IO) {
                                                            try {
                                                                val client = HydraApi.getClient()
                                                                val rejectRequest = Request.Builder()
                                                                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests/${request.id}/refuse")
                                                                    .patch("".toRequestBody())
                                                                    .build()
                                                                client.newCall(rejectRequest).execute().use {
                                                                    if (it.isSuccessful) {
                                                                        refreshProfile()
                                                                    }
                                                                }
                                                            } catch (e: Exception) { e.printStackTrace() }
                                                        }
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Recusar")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        if (!profile?.friends.isNullOrEmpty()) {
                            Text(
                                text = "Amigos (${profile?.friends?.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 12.dp),
                                textAlign = TextAlign.Start
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(profile?.friends ?: emptyList()) { friend ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(70.dp)
                                            .clickable {
                                                navController.navigate(MainActivityRoutes.Profile.route.replace("{userId}", friend.id ?: ""))
                                            }
                                    ) {
                                        AsyncImage(
                                            model = friend.profileImageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Text(
                                            text = friend.displayName ?: "Amigo",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        if (isMe) {
                            Spacer(modifier = Modifier.height(48.dp))
                            OutlinedButton(
                                onClick = {
                                    Settings.accessToken = ""
                                    Settings.refreshToken = ""
                                    Settings.userId = ""
                                    Settings.tokenExpiration = 0L
                                    isLoggedIn = false
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SAIR DA CONTA", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("Você não está logado.", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Faça login para sincronizar sua conta e acessar recursos exclusivos.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://auth.hydralauncher.gg"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ENTRAR / REGISTRAR")
                }
            }
        }
    }

    if (showAddFriendDialog) {
        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            title = { Text("Adicionar Amigo") },
            text = {
                OutlinedTextField(
                    value = friendCodeToAdd,
                    onValueChange = { friendCodeToAdd = it },
                    label = { Text("ID do Amigo") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    addFriend(friendCodeToAdd)
                    showAddFriendDialog = false
                }) { Text("Adicionar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Denunciar Perfil") },
            text = {
                Column {
                    val reasons = listOf("hate", "sexual_content", "violence", "spam", "other")
                    reasons.forEach { reason ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { reportReason = reason }) {
                            RadioButton(selected = reportReason == reason, onClick = { reportReason = reason })
                            Text(reason.replace("_", " ").replaceFirstChar { it.uppercase() })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportDescription,
                        onValueChange = { reportDescription = it },
                        label = { Text("Descrição") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    reportUser(reportReason, reportDescription)
                    showReportDialog = false
                }) { Text("Denunciar") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    ElevatedCard(
        modifier = Modifier.width(110.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun handleImageUpload(
    uri: Uri,
    isProfileImage: Boolean,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: (HydraProfile) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val fileName = uri.lastPathSegment ?: "image.png"
            val extension = if (fileName.contains(".")) fileName.substringAfterLast(".") else "png"

            val client = HydraApi.getClient()
            val gson = Gson()

            // 1. Get Presigned URL
            val presignedEndpoint = if (isProfileImage) "/presigned-urls/profile-image" else "/presigned-urls/background-image"
            val presignedBody = mapOf(
                "imageExt" to extension,
                "imageLength" to bytes.size
            )
            val presignedRequest = Request.Builder()
                .url("https://hydra-api-us-east-1.losbroxas.org$presignedEndpoint")
                .post(gson.toJson(presignedBody).toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val responseData = client.newCall(presignedRequest).execute().use { response ->
                if (response.isSuccessful) {
                    gson.fromJson(response.body?.string(), Map::class.java)
                } else null
            } ?: return@launch

            val presignedUrl = responseData["presignedUrl"] as? String ?: return@launch
            val finalImageUrl = (if (isProfileImage) responseData["profileImageUrl"] else responseData["backgroundImageUrl"]) as? String
                ?: presignedUrl.substringBefore("?")

            // 2. Upload binary data to Presigned URL
            val mimeType = contentResolver.getType(uri) ?: "image/png"
            val uploadRequest = Request.Builder()
                .url(presignedUrl)
                .put(bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                .build()

            val uploadSuccess = client.newCall(uploadRequest).execute().use { it.isSuccessful }

            if (uploadSuccess) {
                // 3. Update Profile with the final URL
                val patchBody = if (isProfileImage) {
                    mapOf("profileImageUrl" to finalImageUrl)
                } else {
                    mapOf("backgroundImageUrl" to finalImageUrl)
                }

                val patchRequest = Request.Builder()
                    .url("https://hydra-api-us-east-1.losbroxas.org/profile")
                    .patch(gson.toJson(patchBody).toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val patchSuccess = client.newCall(patchRequest).execute().use { it.isSuccessful }

                if (patchSuccess) {
                    // Refresh Profile to get updated data
                    val refreshRequest = Request.Builder()
                        .url("https://hydra-api-us-east-1.losbroxas.org/profile/me")
                        .build()
                    client.newCall(refreshRequest).execute().use { refreshResponse ->
                        if (refreshResponse.isSuccessful) {
                            val newProfile = gson.fromJson(refreshResponse.body?.string(), HydraProfile::class.java)

                            Settings.updateFromProfile(newProfile)

                            withContext(Dispatchers.Main) {
                                onSuccess(newProfile)
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
