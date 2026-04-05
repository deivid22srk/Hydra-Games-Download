package com.rk.terminal.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.rk.terminal.ui.routes.MainActivityRoutes

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

    LaunchedEffect(Unit) {
        while(true) {
            val currentLoginState = Settings.accessToken.isNotBlank()
            if (isLoggedIn != currentLoginState) {
                isLoggedIn = currentLoginState
            }
            if (isLoggedIn && Settings.refreshToken.isNotBlank()) {
                val timeUntilExpiration = Settings.tokenExpiration - System.currentTimeMillis()
                if (timeUntilExpiration > 0 && timeUntilExpiration < 300000) {
                    tokenRefreshIndicator = true
                    withContext(Dispatchers.IO) {
                        try {
                            HydraApi.revalidateSession()
                            android.util.Log.d("ProfileScreen", "Token revalidated. New expiration: ${Settings.tokenExpiration}")
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileScreen", "Token revalidation error", e)
                        } finally {
                            withContext(Dispatchers.Main) {
                                tokenRefreshIndicator = false
                            }
                        }
                    }
                }
            }
            delay(30000)
        }
    }

    val refreshProfile = {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val gson = Gson()

                val badgesRequest = Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/badges").build()
                client.newCall(badgesRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val type = object : TypeToken<List<HydraBadge>>() {}.type
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
                        withContext(Dispatchers.Main) {
                            isLoggedIn = false
                        }
                    }
                }

                if (isMe) {
                    val friendRequestsUrl = "https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests"
                    val friendRequestsReq = Request.Builder().url(friendRequestsUrl).build()
                    client.newCall(friendRequestsReq).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            try {
                                val incomingRequests = try {
                                    gson.fromJson(body, object : TypeToken<List<HydraFriendRequest>>() {}.type)
                                        ?: emptyList()
                                } catch (e: Exception) {
                                    val wrappedResponse = gson.fromJson(body, HydraFriendRequestsResponse::class.java)
                                    wrappedResponse?.incoming ?: emptyList()
                                }
                                if (incomingRequests.isNotEmpty()) {
                                    friendRequests = HydraFriendRequestsResponse(incoming = incomingRequests)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ProfileScreen", "Error parsing friend requests", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
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

    val formatPlayTime: (Long) -> String = { seconds ->
        val minutes = seconds / 60
        if (minutes < 60) "$minutes min" else "${minutes / 60}h"
    }

    /* ---- scaffold body ---- */

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
                            IconButton(onClick = {
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
                            }) {
                                Icon(Icons.Default.Block, contentDescription = "Bloquear")
                            }
                        }
                    } else if (tokenRefreshIndicator) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
                    /* ---- Banner ---- */
                    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                        AsyncImage(
                            model = profile?.backgroundImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(240.dp).blur(8.dp),
                            contentScale = ContentScale.Crop,
                            alpha = 0.4f
                        )
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
                            Row(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                                FilledTonalIconButton(onClick = { /* profile image */ }) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Trocar Foto")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalIconButton(onClick = { /* background image */ }) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Trocar Fundo")
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.size(130.dp).align(Alignment.BottomCenter),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(5.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            tonalElevation = 8.dp,
                            shadowElevation = 16.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), Color.Transparent)
                                            ),
                                            CircleShape
                                        )
                                )
                                AsyncImage(
                                    model = profile?.profileImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().padding(8.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                if (isEditing) {
                                    IconButton(
                                        onClick = { /* launch profile image chooser */ },
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
                                    Text(text = id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar ID", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(19.dp))

                        Text(
                            text = profile?.bio ?: "Nenhuma biografia disponível.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        /* ---- Stats ---- */
                        profile?.stats?.let { stats ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatCard(Icons.Default.EmojiEvents, "${stats.unlockedAchievementSum ?: 0}", "Conquistas")
                                StatCard(Icons.Default.History, formatPlayTime(stats.totalPlayTimeInSeconds?.value?.toLong() ?: 0L), "Tempo total")
                                StatCard(Icons.Default.Star, "${profile?.karma ?: 0}", "Karma")
                            }
                        }

                        /* ---- My Library button ---- */
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

                        /* ---- Recent Games ---- */
                        if (!profile?.recentGames.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Atividade Recente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
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
                                            AsyncImage(model = game.iconUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(game.title ?: "", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatPlayTime(game.playTimeInSeconds ?: 0L), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        /* ---- Badges ---- */
                        if (!profile?.badges.isNullOrEmpty()) {
                            Text(text = "Emblemas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp), textAlign = TextAlign.Start)
                            LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(profile?.badges ?: emptyList()) { badgeName ->
                                    val badgeDef = globalBadges.find { it.name == badgeName }
                                    if (badgeDef != null) {
                                        Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
                                            AsyncImage(model = badgeDef.badge?.url, contentDescription = badgeDef.title, modifier = Modifier.padding(8.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        /* ---- Incoming Friend Requests ---- */
                        if (isMe && !friendRequests?.incoming.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Solicitações de Amizade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(32.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = "${friendRequests?.incoming?.size}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
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
                                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                AsyncImage(model = requester.profileImageUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(text = requester.displayName ?: "Usuário", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(text = "Pedido de amizade", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                /* Accept */
                                                Button(
                                                    onClick = {
                                                        scope.launch(Dispatchers.IO) {
                                                            try {
                                                                val client = HydraApi.getClient()
                                                                val body = Gson().toJson(mapOf("requestState" to "ACCEPTED"))
                                                                    .toRequestBody("application/json".toMediaTypeOrNull())
                                                                val req = Request.Builder()
                                                                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests/${request.id}")
                                                                    .patch(body)
                                                                    .build()
                                                                client.newCall(req).execute().use {
                                                                    if (it.isSuccessful) {
                                                                        withContext(Dispatchers.Main) { refreshProfile() }
                                                                    }
                                                                }
                                                            } catch (e: Exception) { e.printStackTrace() }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                                    Text("Aceitar", color = Color.White)
                                                }
                                                /* Refuse */
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch(Dispatchers.IO) {
                                                            try {
                                                                val client = HydraApi.getClient()
                                                                val body = Gson().toJson(mapOf("requestState" to "REFUSED"))
                                                                    .toRequestBody("application/json".toMediaTypeOrNull())
                                                                val req = Request.Builder()
                                                                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests/${request.id}")
                                                                    .patch(body)
                                                                    .build()
                                                                client.newCall(req).execute().use {
                                                                    if (it.isSuccessful) {
                                                                        withContext(Dispatchers.Main) { refreshProfile() }
                                                                    }
                                                                }
                                                            } catch (e: Exception) { e.printStackTrace() }
                                                        }
                                                    }
                                                ) {
                                                    Text("Recusar")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        /* ---- Outgoing Friend Requests ---- */
                        if (isMe && !friendRequests?.outgoing.isNullOrEmpty()) {
                            Text(text = "Solicitações Enviadas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface)

                            friendRequests?.outgoing?.forEach { request ->
                                val target = request.userB
                                if (target != null) {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                AsyncImage(model = target.profileImageUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(text = target.displayName ?: "Usuário", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(text = "Aguardando resposta...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    scope.launch(Dispatchers.IO) {
                                                        try {
                                                            val client = HydraApi.getClient()
                                                            val cancelReq = Request.Builder()
                                                                .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests/${request.id}")
                                                                .delete()
                                                                .build()
                                                            client.newCall(cancelReq).execute().use {
                                                                if (it.isSuccessful) {
                                                                    withContext(Dispatchers.Main) { refreshProfile() }
                                                                }
                                                            }
                                                        } catch (e: Exception) { e.printStackTrace() }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        /* ---- Friends ---- */
                        if (!profile?.friends.isNullOrEmpty()) {
                            Text(text = "Amigos (${profile?.friends?.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 12.dp), textAlign = TextAlign.Start)
                            LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(profile?.friends ?: emptyList()) { friend ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp).clickable { navController.navigate(MainActivityRoutes.Profile.route.replace("{userId}", friend.id ?: "")) }) {
                                        AsyncImage(model = friend.profileImageUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                        Text(text = friend.displayName ?: "Amigo", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }

                        /* ---- Logout ---- */
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
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
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
                /* ---- Not logged in ---- */
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(24.dp))
                Text("Você não está logado.", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Faça login para sincronizar sua conta e acessar recursos exclusivos.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
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
                TextButton(onClick = {
                    Settings.accessToken = ""
                    Settings.refreshToken = ""
                    Settings.userId = ""
                    Settings.tokenExpiration = 0L
                }) {
                    Text("Limpar dados de login")
                }
            }
        }
    }

    /* ---- Add Friend Dialog ---- */
    if (showAddFriendDialog) {
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

    /* ---- Report Dialog ---- */
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Denunciar Perfil") },
            text = {
                Column {
                    val reasons = listOf("hate" to "Ódio", "sexual_content" to "Conteúdo Sexual", "violence" to "Violência", "spam" to "Spam", "other" to "Outro")
                    reasons.forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { reportReason = value }) {
                            RadioButton(selected = reportReason == value, onClick = { reportReason = value })
                            Text(label)
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
                    scope.launch(Dispatchers.IO) {
                        try {
                            val client = HydraApi.getClient()
                            val body = Gson().toJson(mapOf("reason" to reportReason, "description" to reportDescription))
                                .toRequestBody("application/json".toMediaTypeOrNull())
                            val request = Request.Builder()
                                .url("https://hydra-api-us-east-1.losbroxas.org/users/${profile?.id}/report")
                                .post(body)
                                .build()
                            client.newCall(request).execute().use { }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
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
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
