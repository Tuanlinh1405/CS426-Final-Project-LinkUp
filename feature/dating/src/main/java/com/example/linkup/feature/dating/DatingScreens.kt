package com.example.linkup.feature.dating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.linkup.core.designsystem.component.ChoiceChip
import com.example.linkup.core.designsystem.component.EmptyState
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.PrimaryButton
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.data.model.User
import com.example.linkup.core.designsystem.theme.LinkCanvas
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPink
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

@Composable
fun DatingProfileScreen(
    profile: DatingProfile,
    onBack: () -> Unit,
    onSave: (DatingProfile) -> Unit,
    onExplore: () -> Unit,
    onPickPhoto: () -> Unit,
    onDeletePhoto: (String) -> Unit,
    isSaving: Boolean = false
) {
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var interests by remember(profile) { mutableStateOf(profile.interests.toSet()) }
    var lookingFor by remember(profile) { mutableStateOf(profile.lookingFor) }
    var preferredGender by remember(profile) { mutableStateOf(profile.preferredGender ?: "ANY") }
    var minAge by remember(profile) { mutableStateOf(profile.minAge?.toString().orEmpty()) }
    var maxAge by remember(profile) { mutableStateOf(profile.maxAge?.toString().orEmpty()) }
    val availableInterests = listOf("Travel", "Design", "Coffee", "Music", "Sports")
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        ScreenHeader("Dating Profile", onBack)
        DatingPhotoStrip(
            photos = profile.photos,
            fallbackUrl = profile.avatarUrl,
            initials = profile.initials,
            isBusy = isSaving,
            onPickPhoto = onPickPhoto,
            onDeletePhoto = onDeletePhoto
        )
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (profile.age > 0) "${profile.name}, ${profile.age}" else profile.name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp
            )
            if (profile.username.isNotBlank()) Text(profile.username, color = LinkMuted)
            LinkUpField(bio, { bio = it }, "About me", singleLine = false)
            Text("Interests", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                availableInterests.forEach { interest ->
                    ChoiceChip(
                        interest,
                        selected = interest in interests,
                        onClick = {
                            interests = if (interest in interests) interests - interest else interests + interest
                        }
                    )
                }
            }
            Text("Looking for", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ChoiceChip("Relationship", lookingFor == LookingFor.RELATIONSHIP) { lookingFor = LookingFor.RELATIONSHIP }
                ChoiceChip("Friendship", lookingFor == LookingFor.FRIENDSHIP) { lookingFor = LookingFor.FRIENDSHIP }
            }
            Text("Preferred gender", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("ANY", "FEMALE", "MALE").forEach { gender ->
                    ChoiceChip(gender.lowercase().replaceFirstChar { it.uppercase() }, preferredGender == gender) {
                        preferredGender = gender
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinkUpField(minAge, { minAge = it.filter(Char::isDigit) }, "Min age", modifier = Modifier.weight(1f))
                LinkUpField(maxAge, { maxAge = it.filter(Char::isDigit) }, "Max age", modifier = Modifier.weight(1f))
            }
            PrimaryButton("Save dating profile", onClick = {
                onSave(
                    profile.copy(
                        bio = bio.trim(),
                        interests = interests.toList(),
                        lookingFor = lookingFor,
                        preferredGender = preferredGender,
                        minAge = minAge.toIntOrNull(),
                        maxAge = maxAge.toIntOrNull()
                    )
                )
            })
            Text("Explore Dating", color = LinkPurple, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onExplore).padding(vertical = 8.dp))
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Horizontal strip of the user's dating photos with an add tile and per-photo remove. */
@Composable
private fun DatingPhotoStrip(
    photos: List<DatingPhoto>,
    fallbackUrl: String?,
    initials: String,
    isBusy: Boolean,
    onPickPhoto: () -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
    ) {
        item {
            Box(
                Modifier
                    .width(120.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFC0D2), Color(0xFF6A3AA8))))
                    .clickable(enabled = !isBusy, onClick = onPickPhoto),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Add photo", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        items(photos, key = { it.id }) { photo ->
            Box(Modifier.width(120.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
                AsyncImage(
                    model = fadeInRequest(photo.photoUrl),
                    contentDescription = "Dating photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(enabled = !isBusy, onClick = { onDeletePhoto(photo.id) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (photos.isEmpty() && !fallbackUrl.isNullOrBlank()) {
            item {
                Box(Modifier.width(120.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
                    AsyncImage(
                        model = fadeInRequest(fallbackUrl),
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        if (photos.isEmpty() && fallbackUrl.isNullOrBlank()) {
            item {
                Box(
                    Modifier
                        .width(120.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFD8C6A5), Color(0xFF5D7158)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun fadeInRequest(url: String): ImageRequest =
    ImageRequest.Builder(LocalContext.current)
        .data(url)
        .crossfade(true)
        .build()

/** Shows a remote photo when available, otherwise the candidate's initials on a gradient. */
@Composable
private fun CandidatePhoto(candidate: DatingCandidate, initialsFontSize: androidx.compose.ui.unit.TextUnit = 28.sp) {
    if (!candidate.photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = fadeInRequest(candidate.photoUrl),
            contentDescription = "${candidate.user.name}'s photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFD8C6A5), Color(0xFF5D7158))))) {
            Text(candidate.user.initials, color = Color.White, fontSize = initialsFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun DatingDiscoverScreen(
    candidate: DatingCandidate?,
    onProfile: () -> Unit,
    onMatches: () -> Unit,
    onOpenProfile: (DatingCandidate) -> Unit,
    onPass: () -> Unit,
    onLike: () -> Unit,
    onReviewPassed: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(LinkCanvas)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Discover", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("Matches", color = LinkPink, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onMatches))
            Text("  ⚙", modifier = Modifier.clickable(onClick = onProfile))
        }
        if (candidate == null) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState("No more profiles", "You have seen all available profiles for now.")
                PrimaryButton("Review passed profiles", onReviewPassed, Modifier.padding(horizontal = 28.dp))
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .clickable { onOpenProfile(candidate) }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { onOpenProfile(candidate) }
                        .background(Brush.verticalGradient(listOf(Color(0xFFD8C6A5), Color(0xFF5D7158)))),
                    contentAlignment = Alignment.Center
                ) {
                    CandidatePhoto(candidate, initialsFontSize = 28.sp)
                }
                Column(Modifier.padding(18.dp)) {
                    Text("${candidate.user.name}, ${candidate.age}", fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                    Text(candidate.distanceKm?.let { "${candidate.user.username} · ${it} km away" } ?: candidate.user.username, color = LinkMuted)
                    Text(candidate.bio, modifier = Modifier.padding(top = 10.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                DatingButton("×", Color.White, LinkMuted, onPass)
                DatingButton("♥", LinkPink, Color.White, onLike)
            }
        }
    }
}

@Composable
fun CandidateProfileScreen(
    candidate: DatingCandidate,
    onBack: () -> Unit,
    onViewProfile: () -> Unit,
    onPass: () -> Unit,
    onLike: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(LinkCanvas).verticalScroll(rememberScrollState())) {
        ScreenHeader("Dating Profile", onBack)
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFD8C6A5), Color(0xFF5D7158)))),
            contentAlignment = Alignment.Center
        ) {
            CandidatePhoto(candidate, initialsFontSize = 30.sp)
        }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${candidate.user.name}, ${candidate.age}", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            Text(
                candidate.distanceKm?.let { "${candidate.user.username} · ${it} km away" } ?: candidate.user.username,
                color = LinkMuted
            )
            Text(candidate.bio.ifBlank { "No bio yet." }, fontSize = 16.sp)
            Text("Interests", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                candidate.interests.forEach { ChoiceChip(it, selected = true) }
            }
            if (candidate.interests.isEmpty()) Text("No interests added yet.", color = LinkMuted)
            TextButton(onClick = onViewProfile) {
                Text("View full profile")
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DatingButton("×", Color.White, LinkMuted, onPass)
                DatingButton("♥", LinkPink, Color.White, onLike)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun PublicProfileScreen(candidate: DatingCandidate, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LinkCanvas).verticalScroll(rememberScrollState())) {
        ScreenHeader("Profile", onBack)
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFD8C6A5), Color(0xFF5D7158)))),
            contentAlignment = Alignment.Center
        ) {
            CandidatePhoto(candidate, initialsFontSize = 30.sp)
        }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(candidate.user.name, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            Text(candidate.user.username, color = LinkMuted)
            Text(candidate.user.bio.ifBlank { candidate.bio.ifBlank { "No bio yet." } })
            Text("Posts", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("Public posts will appear here.", color = LinkMuted)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DatingButton(text: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(background).clickable(onClick = onClick).padding(horizontal = 23.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = foreground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DatingMatchScreen(me: User, match: DatingMatch?, onChat: () -> Unit, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0C0B10)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.Center) {
            listOf(me.initials, match?.user?.initials ?: "?").forEach { initials ->
                Box(Modifier.padding(horizontal = 4.dp).clip(CircleShape).background(Brush.linearGradient(listOf(LinkPurple, LinkPink))).padding(32.dp)) { Text(initials, color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("It's a Match!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 31.sp)
        Text("You and ${match?.user?.name ?: "your match"} liked each other", color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(8.dp))
        PrimaryButton("Chat Now", onChat, Modifier.padding(top = 28.dp))
        Text("Continue Exploring", color = Color.White, modifier = Modifier.clickable(onClick = onContinue).padding(18.dp))
    }
}

@Composable
fun DatingMatchesScreen(matches: List<DatingMatch>, onBack: () -> Unit, onChat: (DatingMatch) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Matches", onBack)
        Text("New matches", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            matches.forEach { match ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onChat(match) }) {
                    Box(Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(LinkPink, LinkPurple))).padding(3.dp)) {
                        Box(Modifier.clip(CircleShape).background(Color.White).padding(3.dp)) { Text(match.user.initials, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold) }
                    }
                    Text(match.user.name, fontSize = 12.sp)
                }
            }
        }
        Text("Messages", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        matches.forEach { match ->
            Row(Modifier.fillMaxWidth().clickable { onChat(match) }.padding(16.dp)) {
                Box(Modifier.clip(CircleShape).background(LinkPurpleSoft).padding(13.dp)) { Text(match.user.initials, color = LinkPurple, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 12.dp)) { Text(match.user.name, fontWeight = FontWeight.Bold); Text("Say hello to your new match!", color = LinkMuted, fontSize = 12.sp) }
            }
        }
        if (matches.isEmpty()) EmptyState("No matches yet", "Like someone to start connecting.")
    }
}
