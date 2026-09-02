package com.example.linkup.feature.reels

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.linkup.core.ui.PrimaryButton
import com.example.linkup.core.ui.ScreenHeader
import com.example.linkup.data.model.UserResponse
import com.example.linkup.data.reels.ReelRepository
import com.example.linkup.feature.reels.player.ReelPlayer
import com.example.linkup.feature.reels.upload.SelectedVideo
import com.example.linkup.feature.reels.upload.prepareVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun UploadReelScreen(me: UserResponse?, repository: ReelRepository, onBack: () -> Unit, onPublished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf<SelectedVideo?>(null) }
    var caption by remember { mutableStateOf("") }
    var selectedCover by remember { mutableIntStateOf(0) }
    var requestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var preparing by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }
    val latestSelection by rememberUpdatedState(selection)
    DisposableEffect(Unit) { onDispose { latestSelection?.cleanup() } }
    BackHandler(enabled = uploading || preparing) { }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            preparing = true; error = null
            scope.launch {
                try { val picked = prepareVideo(context, uri); selection?.cleanup(); selection = picked; selectedCover = 0; requestId = UUID.randomUUID().toString() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Cannot select this video." }
                finally { preparing = false }
            }
        }
    }
    fun choose() { if (!uploading && !preparing) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("Upload Reel", { if (!uploading && !preparing) onBack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val selected = selection
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAE7F0)), contentAlignment = Alignment.Center) {
                if (selected != null) ReelPlayer(selected.file.toURI().toString(), selected.durationMs, active = !uploading && !preparing, muted = false, modifier = Modifier.fillMaxSize())
                else Column(Modifier.fillMaxSize().clickable(enabled = !preparing, onClick = ::choose), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("＋", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Select a video", fontWeight = FontWeight.Bold)
                    Text("MP4 · H.264 · no duration limit · 50 MB")
                }
                if (preparing) CircularProgressIndicator()
            }
            TextButton(onClick = ::choose, enabled = !preparing && !uploading) { Text(if (selected == null) "Choose video" else "Replace video · ${selected.durationMs / 1000}s") }
            OutlinedTextField(caption, { if (it.length <= 2200) caption = it }, label = { Text("Caption") }, placeholder = { Text("Tell the story behind your reel…") },
                supportingText = { Text("${caption.length}/2200") }, modifier = Modifier.fillMaxWidth(), enabled = !uploading, minLines = 3, maxLines = 6)
            if (selected != null && selected.thumbnails.isNotEmpty()) {
                Text("Choose cover", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    selected.thumbnails.forEachIndexed { index, image ->
                        AsyncImage(image, "Cover ${index + 1}", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(8.dp)).border(if (index == selectedCover) 3.dp else 1.dp,
                                if (index == selectedCover) MaterialTheme.colorScheme.primary else Color.LightGray, RoundedCornerShape(8.dp)).clickable(enabled = !uploading) { selectedCover = index })
                    }
                }
            }
            Text("Publishing as ${me?.fullName ?: me?.username ?: "—"}", modifier = Modifier.padding(vertical = 16.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp)) }
        }
        Column(Modifier.padding(16.dp)) {
            if (uploading) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(if (progress >= 1f) "Processing video…" else "Uploading ${(progress * 100).toInt()}%")
                TextButton(onClick = { uploadJob?.cancel(); error = "Upload cancelled. You can retry." }) { Text("Cancel upload") }
            }
            PrimaryButton(if (uploading) "Publishing…" else "Publish Reel", {
                val video = selection ?: return@PrimaryButton
                uploading = true; progress = 0f; error = null
                uploadJob = scope.launch {
                    try {
                        repository.upload(requestId, caption.trim(), video.file, video.thumbnails.getOrNull(selectedCover)) { value -> scope.launch { progress = value } }
                        onPublished()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Upload failed. Please retry." }
                    finally { uploading = false }
                }
            }, enabled = selection != null && me != null && !uploading && !preparing)
        }
    }
}
