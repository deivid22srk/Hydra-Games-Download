package com.rk.terminal.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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

/* ============================================================
   PALETA DE COR EXTRA — pode remover quando theme tiver
   ============================================================ */
val AccentGradient = listOf(
    Color(0xFF7F5AF0),
    Color(0xFF6246EA)
)
val AccentGradientBrush = Brush.horizontalGradient(AccentGradient)
val CardBg = Color(0xFF1A1A2E)
val CardBgLight = Color(0xFF22223E)
val SubtleTextColor = Color(0xFF9999BB)

/* ============================================================
   PROFILE SCREEN COMPLETO
   ============================================================ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, userIdArg: String? = null) {
    val userId = if (userIdArg.isNullOrBlank()) null else userIdArg
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isMe = userId == null || (Settings.userId.isNotBlank() && userId == Settings.userId)

    /* ------------ state ------------ */
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
    var incomingRequests by remember { mutableStateOf<List<FriendRequestItem>>(emptyList()) }
    var outgoingRequests by remember { mutableStateOf<List<FriendRequestItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    var editDisplayName by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var isLoggedIn by remember { mutableStateOf(Settings.accessToken.isNotBlank()) }

    /* ------------ dialogs ------------ */
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var friendCodeToAdd by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("hate") }
    var reportDescription by remember { mutableStateOf("") }
    var showConfirmRemoveFriend by remember { mutableStateOf<String?>(null) }

    /* ------------ token refresh ------------ */
    LaunchedEffect(Unit) {
        while (true) {
            val loggedIn = Settings.accessToken.isNotBlank()
            if (isLoggedIn != loggedIn) isLoggedIn = loggedIn
            if (loggedIn && Settings.refreshToken.isNotBlank()) {
                val timeLeft = Settings.tokenExpiration - System.currentTimeMillis()
                if (timeLeft > 0 && timeLeft < 300_000) {
                    withContext(Dispatchers.IO) {
                        try { HydraApi.revalidateSession() }
                        catch (e: Exception) { android.util.Log.e("ProfileScreen", "token refresh", e) }
                    }
                }
            }
            delay(30_000)
        }
    }

    /* ------------ refreshProfile ------------ */
    val refreshProfile = {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val gson = Gson()

                // Badges
                client.newCall(Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/badges").build()).execute().use { r ->
                    if (r.isSuccessful) {
                        globalBadges = gson.fromJson(r.body?.string(), object : TypeToken<List<HydraBadge>>() {}.type) ?: emptyList()
                    }
                }

                // Profile
                val profileUrl = if (userId == null) "https://hydra-api-us-east-1.losbroxas.org/profile/me"
                    else "https://hydra-api-us-east-1.losbroxas.org/users/$userId"
                client.newCall(Request.Builder().url(profileUrl).build()).execute().use { r ->
                    if (r.isSuccessful) {
                        val p = gson.fromJson(r.body?.string(), HydraProfile::class.java)
                        profile = p
                        if (isMe) Settings.updateFromProfile(p)
                    } else if (r.code == 401) {
                        withContext(Dispatchers.Main) { isLoggedIn = false }
                    }
                }

                // Friend requests (APENAS meu perfil)
                if (isMe) {
                    incomingRequests = emptyList()
                    outgoingRequests = emptyList()
                    client.newCall(Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests").build()).execute().use { r ->
                        if (r.isSuccessful) {
                            val body = r.body?.string() ?: ""
                            android.util.Log.d("FriendRequests", "Raw API response: $body")
                            try {
                                val allRequests: List<FriendRequestItem> = gson.fromJson(
                                    body,
                                    object : TypeToken<List<FriendRequestItem>>() {}.type
                                ) ?: emptyList()
                                android.util.Log.d("FriendRequests", "Parsed ${allRequests.size} requests")
                                incomingRequests = allRequests.filter { it.type == "RECEIVED" }
                                outgoingRequests = allRequests.filter { it.type == "SENT" }
                            } catch (e: Exception) {
                                android.util.Log.e("FriendRequests", "Parse error", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    LaunchedEffect(userId, isLoggedIn) {
        if (isLoggedIn) refreshProfile() else { isLoading = false; profile = null }
    }

    /* ------------ actions ------------ */
    val formatPlayTime: (Long) -> String = { s ->
        val m = s / 60
        if (m < 60) "$m min" else "${m / 60}h"
    }

    val handleAcceptRequest = { requestId: String ->
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val body = Gson().toJson(FriendRequestActionPayload("ACCEPTED"))
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests/$requestId")
                    .patch(body)
                    .build()
                client.newCall(req).execute().use {
                    if (it.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            incomingRequests = incomingRequests.filter { r -> r.id != requestId }
                            android.widget.Toast.makeText(context, "Amizade aceita!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val handleDeclineRequest = { requestId: String ->
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val body = Gson().toJson(FriendRequestActionPayload("REFUSED"))
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests/$requestId")
                    .patch(body)
                    .build()
                client.newCall(req).execute().use {
                    if (it.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            incomingRequests = incomingRequests.filter { r -> r.id != requestId }
                            android.widget.Toast.makeText(context, "Solicitação recusada", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val handleCancelOutgoingRequest = { requestId: String ->
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val req = Request.Builder()
                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests/$requestId")
                    .delete()
                    .build()
                client.newCall(req).execute().use {
                    if (it.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            outgoingRequests = outgoingRequests.filter { r -> r.id != requestId }
                            android.widget.Toast.makeText(context, "Solicitação cancelada", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isMe) "Perfil" else profile?.displayName ?: "Perfil", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        if (!isNewUserFriendlyId(profile?.id)) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${getDisplayNameInitial(profile?.displayName)}", fontWeight = FontWeight.Bold,
                                modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF3B82F6), CircleShape),
                                fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isLoggedIn && !isLoading) {
                        if (isMe) {
                            if (isEditing) {
                                IconButton(onClick = {
                                    isLoading = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val client = HydraApi.getClient()
                                            val gson = Gson()
                                            val bodyMap = mutableMapOf<String, String>()
                                            if (editDisplayName != profile?.displayName) bodyMap["displayName"] = editDisplayName
                                            if (editBio != profile?.bio) bodyMap["bio"] = editBio
                                            if (bodyMap.isNotEmpty()) {
                                                val bodyJson = gson.toJson(bodyMap)
                                                val requestBody = bodyJson.toRequestBody("application/json".toMediaTypeOrNull())
                                                client.newCall(Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/profile").patch(requestBody).build()).execute().use { r ->
                                                    if (r.isSuccessful) {
                                                        withContext(Dispatchers.Main) {
                                                            isEditing = false
                                                            refreshProfile()
                                                        }
                                                    }
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) { isEditing = false }
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                        finally { withContext(Dispatchers.Main) { isLoading = false } }
                                    }
                                }) { Icon(Icons.Default.Check, contentDescription = "Salvar", tint = Color(0xFF22C55E)) }
                            } else {
                                IconButton(onClick = {
                                    editDisplayName = profile?.displayName ?: ""
                                    editBio = profile?.bio ?: ""
                                    isEditing = true
                                }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                                IconButton(onClick = { showAddFriendDialog = true }) { Icon(Icons.Default.PersonAdd, contentDescription = "Adicionar") }
                            }
                        } else {
                            IconButton(onClick = { showReportDialog = true }) { Icon(Icons.Default.Report, contentDescription = "Denunciar") }
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val client = HydraApi.getClient()
                                        client.newCall(Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/users/${profile?.id}/block").post("".toRequestBody()).build()).execute().use { }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }) { Icon(Icons.Default.Block, contentDescription = "Bloquear", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoggedIn) {
                if (isLoading) {
                    Spacer(modifier = Modifier.height(120.dp))
                    CircularProgressIndicator()
                } else {
                    ProfileBanner(profile)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = editDisplayName, onValueChange = { editDisplayName = it },
                            label = { Text("Nome") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editBio, onValueChange = { editBio = it },
                            label = { Text("Bio") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), minLines = 3
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Text(text = profile?.displayName ?: "Usuário Hydra", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        profile?.id?.let { id ->
                            Text(text = "@$id", style = MaterialTheme.typography.bodySmall, color = SubtleTextColor)
                            CopyIdChip(id, clipboardManager)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = profile?.bio ?: "Nenhuma biografia disponível.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SubtleTextColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    /* stats */
                    profile?.stats?.let { stats ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatCard(Icons.Default.EmojiEvents, "${stats.unlockedAchievementSum ?: 0}", "Conquistas")
                            StatCard(Icons.Default.History, formatPlayTime(stats.totalPlayTimeInSeconds?.value?.toLong() ?: 0L), "Tempo total")
                            StatCard(Icons.Default.Star, "${profile?.karma ?: 0}", "Karma")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    /* library */
                    if (isMe) {
                        Button(
                            onClick = { navController.navigate(MainActivityRoutes.Library.route) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3561)),
                        ) {
                            Icon(Icons.Default.LibraryBooks, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("MINHA BIBLIOTECA", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    /* recent games */
                    if (!profile?.recentGames.isNullOrEmpty()) {
                        RecentGamesRow(profile?.recentGames!!, formatPlayTime, navController)
                    }

                    /* --- SOLICITAÇÕES DE AMIZADE --- */
                    if (isMe) {
                        /* incoming */
                        SectionHeader(title = "Solicitações Recebidas", count = incomingRequests.size)
                        if (incomingRequests.isEmpty()) {
                            EmptyStateText("Nenhuma solicitação de amizade recebida")
                        } else {
                            incomingRequests.forEach { req ->
                                IncomingRequestCard(req, ::handleAcceptRequest, ::handleDeclineRequest)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        /* outgoing */
                        SectionHeader(title = "Solicitações Enviadas", count = outgoingRequests.size)
                        if (outgoingRequests.isEmpty()) {
                            EmptyStateText("Nenhuma solicitação enviada")
                        } else {
                            outgoingRequests.forEach { req ->
                                OutgoingRequestCard(req, ::handleCancelOutgoingRequest)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    /* friends */
                    if (!profile?.friends.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        FriendsRow(navController, profile!!.friends!!)
                    }

                    /* badges */
                    if (!profile?.badges.isNullOrEmpty()) {
                        BadgesRow(profile!!.badges!!, globalBadges)
                    }

                    /* logout */
                    if (isMe) {
                        Spacer(modifier = Modifier.height(40.dp))
                        Row(
                            modifier = Modifier
                                .clickable {
                                    Settings.accessToken = ""
                                    Settings.refreshToken = ""
                                    Settings.userId = ""
                                    Settings.tokenExpiration = 0L
                                    isLoggedIn = false
                                }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SAIR DA CONTA", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
            } else {
                NotLoggedInCard(context)
            }
        }
    }

    /* dialogs */
    if (showAddFriendDialog) {
        SimpleAlertDialog(
            title = "Adicionar Amigo",
            value = friendCodeToAdd, onValueChange = { friendCodeToAdd = it },
            label = "ID do Amigo",
            confirm = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val client = HydraApi.getClient()
                        val body = Gson().toJson(mapOf("friendCode" to friendCodeToAdd)).toRequestBody("application/json".toMediaTypeOrNull())
                        client.newCall(Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests").post(body).build()).execute().use { }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                showAddFriendDialog = false
                friendCodeToAdd = ""
            },
            onDismiss = { showAddFriendDialog = false }
        )
    }

    if (showReportDialog) {
        ReportDialog(
            profile?.id ?: "",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, desc ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val client = HydraApi.getClient()
                        val body = Gson().toJson(mapOf("reason" to reason, "description" to desc)).toRequestBody("application/json".toMediaTypeOrNull())
                        client.newCall(Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/users/${profile?.id}/report").post(body).build()).execute().use { }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        )
    }
}

/* ============================================================
   SUB-COMPONENTS
   ============================================================ */
@Composable
private fun ProfileBanner(profile: HydraProfile?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        /* bg imagem + blur */
        if (profile?.backgroundImageUrl != null) {
            AsyncImage(
                model = profile.backgroundImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(6.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF312E81)))
                )
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xFF0F0F1A).copy(alpha = 0.75f)
                )
            )
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Box {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(120.dp), tint = Color(0xFF7F5AF0).copy(alpha = 0.6f))
            if (profile?.profileImageUrl != null) {
                AsyncImage(
                    model = profile.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(CircleShape).border(4.dp, Color(0xFF0F0F1A), CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun CopyIdChip(id: String, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Surface(
        onClick = {
            clipboard.setText(AnnotatedString(id))
            android.widget.Toast.makeText(LocalContext.current, "ID copiado!", android.widget.Toast.LENGTH_SHORT).show()
        },
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(text = id, style = MaterialTheme.typography.labelSmall, color = SubtleTextColor, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(5.dp))
            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(14.dp), tint = SubtleTextColor)
        }
    }
}

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Surface(
        modifier = Modifier.width(105.dp),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Color(0xFF2a2a45))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(14.dp).fillMaxWidth()
        ) {
            Surface(shape = CircleShape, modifier = Modifier.size(40.dp), contentColor = Color(0xFF7F5AF0)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = SubtleTextColor, fontSize = 10.sp)
        }
    }
}

@Composable
private fun RecentGamesRow(games: List<HydraRecentGame>, fmt: (Long) -> String, navController: NavController) {
    Text("Atividade Recente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(games) { game ->
            Surface(
                modifier = Modifier.width(130.dp).height(120.dp).clip(RoundedCornerShape(14.dp)),
                color = CardBg,
                border = BorderStroke(1.dp, Color(0xFF2a2a45))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(model = game.iconUrl, contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(game.title ?: "", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmt(game.playTimeInSeconds ?: 0L), style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/* ---- FRIEND REQUESTS ---- */
@Composable
private fun SectionHeader(title: String, count: Int = 0) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (count > 0) {
            Badge(
                containerColor = Color(0xFF7F5AF0),
                content = { Text(text = "$count", color = Color.White, fontWeight = FontWeight.Bold) }
            )
        }
    }
}

@Composable
private fun IncomingRequestCard(request: FriendRequestItem, onAccept: (String) -> Unit, onDecline: (String) -> Unit) {
    val reqId = request.id ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Color(0xFF2a2a45))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = request.profileImageUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.displayName ?: "Usuário", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Quer ser seu amigo(a)", color = SubtleTextColor, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { onAccept(reqId) },
                    modifier = Modifier.shadow(2.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF22C55E))
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Aceitar", tint = Color.White)
                }
                IconButton(
                    onClick = { onDecline(reqId) },
                    modifier = Modifier.shadow(2.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Recusar", tint = Color.White)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun OutgoingRequestCard(request: FriendRequestItem, onCancel: (String) -> Unit) {
    val reqId = request.id ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Color(0xFF2a2a45))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = request.profileImageUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.displayName ?: "Usuário", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Aguardando resposta...", color = Color(0xFF60A5FA), fontSize = 12.sp)
            }
            IconButton(
                onClick = { onCancel(reqId) },
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF3B3B5C))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun EmptyStateText(text: String) {
    Text(text, color = SubtleTextColor, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    Spacer(modifier = Modifier.height(16.dp))
}

/* ---- FRIENDS ---- */
@Composable
private fun FriendsRow(navController: NavController, friends: List<HydraFriend>) {
    Text("Amigos (${friends.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(friends) { f ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(64.dp).clickable {
                    navController.navigate(MainActivityRoutes.Profile.route.replace("{userId}", f.id ?: ""))
                }
            ) {
                AsyncImage(model = f.profileImageUrl, contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(CircleShape).border(2.dp, Color(0xFF2a2a45), CircleShape),
                    contentScale = ContentScale.Crop)
                Spacer(modifier = Modifier.height(4.dp))
                Text(f.displayName ?: " ?", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/* ---- BADGES ---- */
@Composable
private fun BadgesRow(badges: List<String>, globalBadges: List<HydraBadge>) {
    Spacer(modifier = Modifier.height(8.dp))
    Text("Emblemas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(badges) { name ->
            val def = globalBadges.find { it.name == name }
            if (def != null) {
                Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = CardBg,
                    border = BorderStroke(1.dp, Color(0xFF2a2a45)), tonalElevation = 2.dp) {
                    AsyncImage(model = def.badge?.url, contentDescription = def.title, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/* ---- NOT LOGGED IN ---- */
@Composable
private fun NotLoggedInCard(context: android.content.Context) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(24.dp).padding(top = 60.dp),
        shape = RoundedCornerShape(20.dp),
        color = CardBg
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(80.dp), tint = SubtleTextColor.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Você não está logado.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Faça login para sincronizar sua conta e acessar recursos exclusivos.",
                color = SubtleTextColor, textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://auth.hydralauncher.gg"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ENTRAR / REGISTRAR")
            }
        }
    }
}

/* ============================================================
   DIALOGS
   ============================================================ */
@Composable
private fun SimpleAlertDialog(
    title: String, value: String, onValueChange: (String) -> Unit,
    label: String, confirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { Button(onClick = confirm) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ReportDialog(userId: String, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var reason by remember { mutableStateOf("hate") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Denunciar") },
        text = {
            Column {
                listOf("hate" to "Ódio", "sexual_content" to "Conteúdo Sexual", "violence" to "Violência", "spam" to "Spam", "other" to "Outro").forEach { (v, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { reason = v }) {
                        RadioButton(selected = reason == v, onClick = { reason = v })
                        Text(l)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(reason, desc); onDismiss() }) { Text("Denunciar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/* ============================================================
   UTILS
   ============================================================ */
private fun isNewUserFriendlyId(id: String?): Boolean {
    if (id.isNullOrBlank()) return false
    return !id.contains("-") || id.length < 16
}
private fun getDisplayNameInitial(name: String?): String {
    return name?.takeIf { it.isNotBlank() }?.substring(0, 1)?.uppercase() ?: "?"
}
