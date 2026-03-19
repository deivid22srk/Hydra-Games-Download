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
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<HydraProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    var editDisplayName by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val profileImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageUpload(it, true, context, scope) { profile = it } }
    }

    val backgroundImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageUpload(it, false, context, scope) { profile = it } }
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
                                withContext(Dispatchers.Main) {
                                    isEditing = false
                                    // Refresh profile
                                    val refreshRequest = Request.Builder()
                                        .url("https://hydra-api-us-east-1.losbroxas.org/profile/me")
                                        .build()
                                    client.newCall(refreshRequest).execute().use { refreshResponse ->
                                        if (refreshResponse.isSuccessful) {
                                            profile = gson.fromJson(refreshResponse.body?.string(), HydraProfile::class.java)
                                        }
                                    }
                                }
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

    val isLoggedIn = Settings.accessToken.isNotBlank()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            withContext(Dispatchers.IO) {
                try {
                    val client = HydraApi.getClient()
                    val request = Request.Builder()
                        .url("https://hydra-api-us-east-1.losbroxas.org/profile/me")
                        .addHeader("Authorization", "Bearer ${Settings.accessToken}")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            profile = Gson().fromJson(response.body?.string(), HydraProfile::class.java)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meu Perfil") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isLoggedIn && !isLoading) {
                        if (isEditing) {
                            IconButton(onClick = {
                                saveProfileChanges()
                            }) {
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
                        }
                        IconButton(onClick = {
                            Settings.accessToken = ""
                            Settings.refreshToken = ""
                            Settings.tokenExpiration = 0L
                            navController.popBackStack()
                        }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Sair")
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
                                .size(80.dp)
                                .align(Alignment.BottomCenter)
                                .offset(y = 40.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 4.dp
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

                    Spacer(modifier = Modifier.height(48.dp))

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
                            text = profile?.displayName ?: "Sem nome",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = profile?.bio ?: "Nenhuma biografia disponível.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
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
            val inputStream = contentResolver.openInputStream(uri) ?: return@launch
            val bytes = inputStream.readBytes()
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

            val presignedUrl = client.newCall(presignedRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val data = gson.fromJson(response.body?.string(), Map::class.java)
                    data["presignedUrl"] as? String
                } else null
            } ?: return@launch

            // 2. Upload binary data to Presigned URL
            val uploadRequest = Request.Builder()
                .url(presignedUrl)
                .put(bytes.toRequestBody(contentResolver.getType(uri)?.toMediaTypeOrNull()))
                .build()

            val uploadSuccess = client.newCall(uploadRequest).execute().use { it.isSuccessful }

            if (uploadSuccess) {
                // 3. Refresh Profile to get the updated image URL
                val refreshRequest = Request.Builder()
                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/me")
                    .build()
                client.newCall(refreshRequest).execute().use { refreshResponse ->
                    if (refreshResponse.isSuccessful) {
                        val newProfile = gson.fromJson(refreshResponse.body?.string(), HydraProfile::class.java)
                        withContext(Dispatchers.Main) {
                            onSuccess(newProfile)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
