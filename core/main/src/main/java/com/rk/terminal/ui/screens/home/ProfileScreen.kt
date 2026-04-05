package com.rk.terminal.ui.screens.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.rk.terminal.ui.routes.MainActivityRoutes
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/* ============================================================
   PROFILE SCREEN COMPLETO
   ============================================================ */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showAllFriends by remember { mutableStateOf(false) }
    var friendCodeToAdd by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("hate") }
    var reportDescription by remember { mutableStateOf("") }

    /* ------------ image crop state ------------ */
    var showCropDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var cropType by remember { mutableStateOf("avatar") }

    /* ------------ image picker ------------ */
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                selectedImageUri = uri
                showCropDialog = true
            }
        }
    )

    val openAvatarPicker = { cropType = "avatar"; imagePickerLauncher.launch("image/*") }
    val openBackgroundPicker = { cropType = "background"; imagePickerLauncher.launch("image/*") }

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

                client.newCall(Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/badges").build()).execute().use { r ->
                    if (r.isSuccessful) {
                        globalBadges = gson.fromJson(r.body?.string(), object : TypeToken<List<HydraBadge>>() {}.type) ?: emptyList()
                    }
                }

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
    val formatPlayTime: (Long) -> String = { s -> val m = s / 60; if (m < 60) "$m min" else "${m / 60}h" }

    val handleAcceptRequest = { requestId: String ->
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val body = Gson().toJson(FriendRequestActionPayload("ACCEPTED"))
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests/$requestId")
                    .patch(body).build()
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
                    .patch(body).build()
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
                    .delete().build()
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
                    Text(if (isMe) "Perfil" else profile?.displayName ?: "Perfil")
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
                                }) { Icon(Icons.Default.Check, contentDescription = "Salvar") }
                            } else {
                                IconButton(onClick = {
                                    editDisplayName = profile?.displayName ?: ""
                                    editBio = profile?.bio ?: ""
                                    isEditing = true
                                }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                                IconButton(onClick = { showAddFriendDialog = true }) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = "Adicionar")
                                }
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
        if (isLoggedIn) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                ProfileContent(
                    profile = profile, isMe = isMe, isEditing = isEditing,
                    editDisplayName = editDisplayName, editBio = editBio,
                    onEditDisplayNameChange = { editDisplayName = it },
                    onEditBioChange = { editBio = it },
                    formatPlayTime = formatPlayTime,
                    clipboardManager = clipboardManager, context = context,
                    incomingRequests = incomingRequests, outgoingRequests = outgoingRequests,
                    globalBadges = globalBadges,
                    onAcceptRequest = { handleAcceptRequest(it) },
                    onDeclineRequest = { handleDeclineRequest(it) },
                    onCancelOutgoingRequest = { handleCancelOutgoingRequest(it) },
                    navController = navController,
                    onShowAllFriends = { showAllFriends = true },
                    onLogout = {
                        Settings.accessToken = ""
                        Settings.refreshToken = ""
                        Settings.userId = ""
                        Settings.tokenExpiration = 0L
                        isLoggedIn = false
                    },
                    onPickAvatar = openAvatarPicker,
                    onPickBackground = openBackgroundPicker
                )
            }
        } else { NotLoggedInCard(context) }
    }

    if (showAddFriendDialog) {
        SimpleAlertDialog(
            title = "Adicionar Amigo", value = friendCodeToAdd, onValueChange = { friendCodeToAdd = it },
            label = "ID do Amigo", confirm = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val client = HydraApi.getClient()
                        val body = Gson().toJson(mapOf("friendCode" to friendCodeToAdd)).toRequestBody("application/json".toMediaTypeOrNull())
                        client.newCall(Request.Builder().url("https://hydra-api-us-east-1.losbroxas.org/profile/friend-requests").post(body).build()).execute().use { }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                showAddFriendDialog = false; friendCodeToAdd = ""
            }, onDismiss = { showAddFriendDialog = false }
        )
    }

    if (showReportDialog) {
        ReportDialog(
            profile?.id ?: "", onDismiss = { showReportDialog = false },
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

    if (showAllFriends && !profile?.friends.isNullOrEmpty()) {
        AllFriendsModal(
            friends = profile!!.friends!!, navController = navController,
            onDismiss = { showAllFriends = false }
        )
    }

    /* ------------ crop dialog ------------ */
    if (showCropDialog && selectedImageUri != null) {
        CropImageDialog(
            imageUri = selectedImageUri!!, cropType = cropType,
            onDismiss = { showCropDialog = false; selectedImageUri = null },
            onCrop = { croppedBytes, extension ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val client = HydraApi.getClient()
                        val gson = Gson()
                        val endpoint = if (cropType == "avatar") "profile-image" else "background-image"
                        val url = "https://hydra-api-us-east-1.losbroxas.org/presigned-urls/$endpoint"
                        val bodyJson = gson.toJson(mapOf("imageExt" to extension, "imageLength" to croppedBytes.size))
                        val req = Request.Builder()
                            .url(url)
                            .post(bodyJson.toRequestBody("application/json".toMediaTypeOrNull())).build()
                        client.newCall(req).execute().use { r ->
                            if (r.isSuccessful) {
                                val resp = gson.fromJson(r.body?.string(), Map::class.java)
                                val presignedUrl = resp["presignedUrl"] as? String
                                val profileImgUrl = resp["profileImageUrl"] as? String
                                val bgImgUrl = resp["backgroundImageUrl"] as? String
                                if (presignedUrl != null) {
                                    val uploadReq = okhttp3.Request.Builder()
                                        .url(presignedUrl)
                                        .put(croppedBytes.toRequestBody(null)).build()
                                    client.newCall(uploadReq).enqueue(object : Callback {
                                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                                            android.util.Log.e("ProfileScreen", "upload failed", e)
                                        }
                                        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                            withContext(Dispatchers.Main) {
                                                if (response.isSuccessful) {
                                                    val newImageUrl = profileImgUrl ?: bgImgUrl
                                                    if (newImageUrl != null) {
                                                        val patchBody = gson.toJson(mapOf(
                                                            if (cropType == "avatar") "profileImageUrl" else "backgroundImageUrl" to newImageUrl
                                                        ))
                                                        scope.launch(Dispatchers.IO) {
                                                            try {
                                                                val patchReq = Request.Builder()
                                                                    .url("https://hydra-api-us-east-1.losbroxas.org/profile")
                                                                    .header("Content-Type", "application/json")
                                                                    .patch(patchBody.toRequestBody("application/json".toMediaTypeOrNull()))
                                                                    .build()
                                                                client.newCall(patchReq).execute().use { p ->
                                                                    if (p.isSuccessful) { refreshProfile(); android.widget.Toast.makeText(context, "Imagem atualizada!", android.widget.Toast.LENGTH_SHORT).show() }
                                                                }
                                                            } catch (e: Exception) { e.printStackTrace() }
                                                        }
                                                    }
                                                }
                                                showCropDialog = false; selectedImageUri = null
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) { showCropDialog = false; selectedImageUri = null }
                    }
                }
            }
        )
    }
}

/* ============================================================
   CROP IMAGE DIALOG — pan/zoom preview + crop
   ============================================================ */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CropImageDialog(
    imageUri: Uri,
    cropType: String,
    onDismiss: () -> Unit,
    onCrop: (ByteArray, String) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                val maxDim = context.resources.displayMetrics.widthPixels.coerceAtMost(1200)
                opts.inSampleSize = calculateInSampleSize(opts, maxDim, maxDim)
                opts.inJustDecodeBounds = false
                context.contentResolver.openInputStream(imageUri)?.use {
                    bitmap = BitmapFactory.decodeStream(it, null, opts)
                }
            } catch (e: Exception) {
                android.util.Log.e("CropImageDialog", "load bitmap", e)
            }
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    if (bitmap != null) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Title
                    Text(
                        text = if (cropType == "avatar") "Foto de Perfil" else "Foto de Fundo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "Arraste e pinche para ajustar o recorte",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )

                    // Crop area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(MaterialTheme.shapes.medium)
                        ) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .matchParentSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 10f)
                                            if (scale > 1f) {
                                                offsetX += pan.x
                                                offsetY += pan.y
                                            } else {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (scale > 1f) {
                                                offsetX += dragAmount.x
                                                offsetY += dragAmount.y
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        scaleX = scale; scaleY = scale
                                        translationX = offsetX
                                        translationY = offsetY
                                    },
                                contentScale = ContentScale.Fit
                            )

                            // Overlay with transparent hole
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val size = this.size
                                val cropSize = if (cropType == "avatar") {
                                    minOf(size.width, size.height) * 0.7f
                                } else {
                                    minOf(size.width, size.height) * 0.65f
                                }
                                val left = (size.width - cropSize) / 2f
                                val top = (size.height - cropSize) / 2f

                                // Dimmed overlay
                                drawRect(
                                    color = Color(0, 0, 0, 0.6f),
                                    topLeft = Offset(0f, 0f),
                                    size = size
                                )
                                // Crop rectangle (clear)
                                drawRect(
                                    color = Color.Transparent,
                                    topLeft = Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(cropSize, cropSize)
                                )
                                // Border
                                drawRect(
                                    color = Color.White.copy(alpha = 0.8f),
                                    topLeft = Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(cropSize, cropSize),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                )
                                if (cropType == "avatar") {
                                    // Round mask hint
                                    val center = Offset(left + cropSize / 2, top + cropSize / 2)
                                    val radius = cropSize / 2
                                    drawCircle(
                                        color = Color.Transparent,
                                        radius = radius,
                                        center = center
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.2f),
                                        center = center,
                                        radius = radius,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                val b = bitmap ?: return@Button
                                val cropSizePx = getBitmapCropSize(b.width, b.height)
                                val scaled = Bitmap.createBitmap(
                                    b,
                                    max(0, (b.width / 2 - cropSizePx / 2 / scale - offsetX / scale).toInt()),
                                    max(0, (b.height / 2 - cropSizePx / 2 / scale - offsetY / scale).toInt()),
                                    min(cropSizePx / scale.toInt().coerceAtLeast(1), max(1, b.width - max(0, (b.width / 2 - cropSizePx / 2 / scale - offsetX / scale).toInt()))),
                                    min(cropSizePx / scale.toInt().coerceAtLeast(1), max(1, b.height - max(0, (b.height / 2 - cropSizePx / 2 / scale - offsetY / scale).toInt())))
                                )
                                val outSize = if (cropType == "avatar") 512 else 1200
                                val resized = Bitmap.createScaledBitmap(scaled, outSize, outSize, true)
                                if (scaled != b) scaled.recycle()
                                val baos = ByteArrayOutputStream()
                                resized.compress(Bitmap.CompressFormat.PNG, 95, baos)
                                resized.recycle()
                                onCrop(baos.toByteArray(), "png")
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height, width) = options to options.run { outHeight to outWidth }.let { (h, w) -> h to w }
    var inSampleSize = 1
    val (outHeight, outWidth) = height to width
    if (outHeight > reqHeight || outWidth > reqWidth) {
        val halfHeight = outHeight / 2
        val halfWidth = outWidth / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun getBitmapCropSize(w: Int, h: Int): Int = minOf(w, h)

/* ============================================================
   PROFILE CONTENT (lazy, scrollable)
   ============================================================ */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(
    profile: HydraProfile?, isMe: Boolean, isEditing: Boolean,
    editDisplayName: String, editBio: String,
    onEditDisplayNameChange: (String) -> Unit, onEditBioChange: (String) -> Unit,
    formatPlayTime: (Long) -> String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager, context: android.content.Context,
    incomingRequests: List<FriendRequestItem>, outgoingRequests: List<FriendRequestItem>,
    globalBadges: List<HydraBadge>,
    onAcceptRequest: (String) -> Unit, onDeclineRequest: (String) -> Unit,
    onCancelOutgoingRequest: (String) -> Unit,
    navController: NavController, onShowAllFriends: () -> Unit, onLogout: () -> Unit,
    onPickAvatar: () -> Unit, onPickBackground: () -> Unit
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { ProfileBanner(profile, isMe, surfaceVariant, onPickBackground, onPickAvatar) }

        if (!isEditing) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = profile?.displayName ?: "Usuário Hydra", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (profile?.bio != null && profile.bio.isNotBlank()) {
                        Text(text = profile.bio, style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    profile?.id?.let { id ->
                        Spacer(modifier = Modifier.height(8.dp))
                        CopyIdChip(id, clipboardManager, context)
                    }
                }
            }
        } else {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(value = editDisplayName, onValueChange = onEditDisplayNameChange,
                        label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editBio, onValueChange = onEditBioChange,
                        label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            }
        }

        if (!profile?.friends.isNullOrEmpty()) {
            item {
                SectionHeader(title = "Amigos", count = profile!!.friends!!.size,
                    trailingButton = if (isMe) "Ver todos" else null) { onShowAllFriends() }
            }
            items(profile!!.friends!!.take(5)) { f ->
                FriendListItem(friend = f, onClick = {
                    navController.navigate(MainActivityRoutes.Profile.route.replace("{userId}", f.id ?: ""))
                })
            }
            if (profile.friends.size > 5) {
                item {
                    TextButton(onClick = onShowAllFriends, modifier = Modifier.fillMaxWidth()) {
                        Text("Ver todos os ${profile.friends.size} amigos")
                    }
                }
            }
        }

        profile?.stats?.let { stats ->
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(title = "Estatísticas")
                    StatCardsRow(formatPlayTime(stats.totalPlayTimeInSeconds?.value?.toLong() ?: 0L),
                        stats.unlockedAchievementSum ?: 0, profile.karma ?: 0)
                }
            }
        }

        if (!profile?.recentGames.isNullOrEmpty()) {
            item { SectionHeader(title = "Atividade Recente") }
            items(profile!!.recentGames!!) { game ->
                Surface(modifier = Modifier.width(120.dp).padding(horizontal = 4.dp, vertical = 2.dp),
                    shape = MaterialTheme.shapes.medium, color = surfaceVariant) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        AsyncImage(model = game.iconUrl, contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(game.title ?: "", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatPlayTime(game.playTimeInSeconds ?: 0L), style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
        }

        if (isMe) {
            if (incomingRequests.isNotEmpty()) {
                item { SectionHeader(title = "Solicitações Recebidas", count = incomingRequests.size) }
                items(incomingRequests) { req -> IncomingRequestCard(req, onAcceptRequest, onDeclineRequest) }
            }
            if (outgoingRequests.isNotEmpty()) {
                item { SectionHeader(title = "Solicitações Enviadas", count = outgoingRequests.size) }
                items(outgoingRequests) { req -> OutgoingRequestCard(req, onCancelOutgoingRequest) }
            }
        }

        if (!profile?.badges.isNullOrEmpty()) {
            item {
                Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge, color = surfaceVariant) {
                    BadgesSection(profile!!.badges!!, globalBadges)
                }
            }
        }

        if (isMe) {
            item {
                Button(onClick = { navController.navigate(MainActivityRoutes.Library.route) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = MaterialTheme.shapes.extraLarge) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MINHA BIBLIOTECA", fontWeight = FontWeight.Bold)
                }
            }
            item {
                TextButton(onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SAIR DA CONTA", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ============================================================
   BANNER — tap avatar or background to change
   ============================================================ */
@Composable
private fun ProfileBanner(profile: HydraProfile?, isMe: Boolean, bgFallback: Color, onPickBackground: () -> Unit, onPickAvatar: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        if (profile?.backgroundImageUrl != null) {
            AsyncImage(model = profile.backgroundImageUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(8.dp), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(bgFallback))
        }

        // Tap background to change
        if (isMe) {
            Box(modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onPickBackground, indication = null,
                    interactionSource = remember { MutableInteractionSource() })
                    .padding(bottom = 60.dp)) {
                // Small hint icon
                Surface(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = CircleShape, color = Color.Black.copy(alpha = 0.4f)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                        tint = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        // Avatar
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 16.dp)) {
            Icon(Icons.Default.Person, contentDescription = null,
                modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            if (profile?.profileImageUrl != null) {
                AsyncImage(model = profile.profileImageUrl, contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            }
            if (isMe) {
                Box(modifier = Modifier.size(96.dp).clickable(onClick = onPickAvatar,
                    interactionSource = remember { MutableInteractionSource() }, indication = null)) {
                    Surface(modifier = Modifier.align(Alignment.BottomEnd).size(28.dp),
                        shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null,
                            modifier = Modifier.padding(6.dp),
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

/* ============================================================
   COPY ID CHIP
   ============================================================ */
@Composable
private fun CopyIdChip(id: String, clipboard: androidx.compose.ui.platform.ClipboardManager, context: android.content.Context) {
    SuggestionChip(
        onClick = {
            clipboard.setText(AnnotatedString(id))
            android.widget.Toast.makeText(context, "ID copiado!", android.widget.Toast.LENGTH_SHORT).show()
        },
        label = {
            Text(text = id, style = MaterialTheme.typography.labelSmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        icon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) }
    )
}

/* ============================================================
   STATS
   ============================================================ */
@Composable
private fun StatCardsRow(playTime: String, achievements: Int, karma: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(Icons.Default.EmojiEvents, "$achievements", "Conquistas")
        StatCard(Icons.Default.History, playTime, "Tempo total")
        StatCard(Icons.Default.Star, "$karma", "Karma")
    }
}

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 1.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

/* ============================================================
   FRIENDS
   ============================================================ */
@Composable
private fun FriendListItem(friend: HydraFriend, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 2.dp), shape = MaterialTheme.shapes.small) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = friend.profileImageUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Text(text = friend.displayName ?: "Usuário", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AllFriendsModal(friends: List<HydraFriend>, navController: NavController, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Amigos", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(friends) { f ->
                    FriendListItem(friend = f, onClick = {
                        onDismiss()
                        navController.navigate(MainActivityRoutes.Profile.route.replace("{userId}", f.id ?: ""))
                    })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

/* ============================================================
   SECTION HEADER
   ============================================================ */
@Composable
private fun SectionHeader(title: String, count: Int = 0, trailingButton: String? = null, onTrailingClick: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (count > 0) { Badge { Text(text = "$count") }; Spacer(modifier = Modifier.width(8.dp)) }
            if (trailingButton != null && onTrailingClick != null) {
                TextButton(onClick = onTrailingClick) { Text(trailingButton) }
            }
        }
    }
}

/* ============================================================
   FRIEND REQUESTS
   ============================================================ */
@Composable
private fun IncomingRequestCard(request: FriendRequestItem, onAccept: (String) -> Unit, onDecline: (String) -> Unit) {
    val reqId = request.id ?: return
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = request.profileImageUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.displayName ?: "Usuário", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text("Quer ser seu amigo(a)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { onAccept(reqId) }) { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                FilledTonalButton(onClick = { onDecline(reqId) }) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun OutgoingRequestCard(request: FriendRequestItem, onCancel: (String) -> Unit) {
    val reqId = request.id ?: return
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = request.profileImageUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.displayName ?: "Usuário", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text("Aguardando resposta...", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { onCancel(reqId) }) { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun BadgesSection(badges: List<String>, globalBadges: List<HydraBadge>) {
    Row(modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Emblemas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp))
        badges.forEach { name ->
            val def = globalBadges.find { it.name == name }
            if (def != null) {
                AsyncImage(model = def.badge?.url, contentDescription = def.title,
                    modifier = Modifier.size(36.dp).clip(CircleShape))
            }
        }
    }
}

/* ============================================================
   NOT LOGGED IN
   ============================================================ */
@Composable
private fun NotLoggedInCard(context: android.content.Context) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Você não está logado.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Faça login para sincronizar sua conta e acessar recursos exclusivos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://auth.hydralauncher.gg"))
            context.startActivity(intent)
        }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ENTRAR / REGISTRAR")
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange,
            label = { Text(label) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = confirm) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ReportDialog(userId: String, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var reason by remember { mutableStateOf("hate") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Denunciar") },
        text = {
            Column {
                listOf("hate" to "Ódio", "sexual_content" to "Conteúdo Sexual",
                    "violence" to "Violência", "spam" to "Spam", "other" to "Outro").forEach { (v, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { reason = v }) {
                        RadioButton(selected = reason == v, onClick = { reason = v })
                        Text(l)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(reason, desc); onDismiss() }) { Text("Denunciar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
