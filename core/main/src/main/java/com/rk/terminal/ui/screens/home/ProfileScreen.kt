package com.rk.terminal.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
    // Update isLoggedIn when Settings.accessToken changes (e.g., after login)
    LaunchedEffect(Unit) {
        while(true) {
            val currentLoginState = Settings.accessToken.isNotBlank()
            if (isLoggedIn != currentLoginState) {
                isLoggedIn = currentLoginState
            }
            kotlinx.coroutines.delay(1000)
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
                title = { Text(if (isMe) "Meu Perfil" else profile?.displayName ?: "Perfil") },
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoggedIn) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        AsyncImage(
                            model = profile?.backgroundImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (isEditing) {
                            IconButton(
                                onClick = { backgroundImageLauncher.launch("image/*") },
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Trocar Fundo")
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(100.dp)
                                .align(Alignment.BottomCenter)
                                .offset(y = 50.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(4.dp, MaterialTheme.colorScheme.surface),
                            tonalElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = profile?.profileImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (isEditing) {
                                    IconButton(
                                        onClick = { profileImageLauncher.launch("image/*") },
                                        modifier = Modifier.fillMaxSize(),
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f))
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Trocar Foto", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(56.dp))

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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(id))
                                    android.widget.Toast.makeText(context, "ID copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "ID: $id",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar ID",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = profile?.bio ?: "Nenhuma biografia disponível.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // User Stats Section
                        profile?.stats?.let { stats ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatCard(
                                    icon = Icons.Default.EmojiEvents,
                                    value = "${stats.unlockedAchievementSum ?: 0}",
                                    label = "Conquistas"
                                )
                                StatCard(
                                    icon = Icons.Default.History,
                                    value = formatPlayTime(stats.totalPlayTimeInSeconds?.value?.toLong() ?: 0L),
                                    label = "Tempo total"
                                )
                                StatCard(
                                    icon = Icons.Default.Star,
                                    value = "${profile?.karma ?: 0}",
                                    label = "Karma"
                                )
                            }
                        }

                        // Library Quick Access
                        if (isMe) {
                            OutlinedButton(
                                onClick = { navController.navigate(MainActivityRoutes.Library.route) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.LibraryBooks, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("VER MINHA BIBLIOTECA")
                            }
                        }

                        // Recent Games Section
                        if (!profile?.recentGames.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Jogos Recentes",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Start
                                )
                                TextButton(onClick = { navController.navigate(MainActivityRoutes.Library.route) }) {
                                    Text("Ver todos")
                                }
                            }
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(profile?.recentGames ?: emptyList()) { game ->
                                    Card(
                                        modifier = Modifier.width(120.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                            AsyncImage(
                                                model = game.iconUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(50.dp).clip(MaterialTheme.shapes.small),
                                                contentScale = ContentScale.Crop
                                            )
                                            Text(game.title ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatPlayTime(game.playTimeInSeconds ?: 0L), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        // Badges Section
                        if (!profile?.badges.isNullOrEmpty()) {
                            Text(
                                text = "Emblemas",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                                textAlign = TextAlign.Start
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(profile?.badges ?: emptyList()) { badgeName ->
                                    val badgeDef = globalBadges.find { it.name == badgeName }
                                    if (badgeDef != null) {
                                        AsyncImage(
                                            model = badgeDef.badge?.url,
                                            contentDescription = badgeDef.title,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!profile?.friends.isNullOrEmpty()) {
                            Text(
                                text = "Amigos (${profile?.friends?.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                textAlign = TextAlign.Start
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = {
                                    Settings.accessToken = ""
                                    Settings.refreshToken = ""
                                    Settings.userId = ""
                                    Settings.tokenExpiration = 0L
                                    isLoggedIn = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SAIR DA CONTA")
                            }
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
    Card(
        modifier = Modifier.width(100.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp).fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
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
