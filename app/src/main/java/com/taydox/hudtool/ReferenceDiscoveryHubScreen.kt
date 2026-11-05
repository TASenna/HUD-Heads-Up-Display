package com.taydox.hudtool

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReferenceDiscoveryHubScreen(
    viewModel: DiscoveryHubViewModel,
    onNavigateToWebView: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A))
                )
            )
    ) {
        // Background Grid and Particles
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FloatingSquaresView(context).apply {
                    setGridColor(0xFF333333.toInt())
                    setAccentColor(0x00C0BF)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // New App Header (mimicking other screens)
            HubAppHeader(onNavigateBack, onNavigateToUpgrade)

            when (uiState.currentTab) {
                HubTab.SEARCH -> SearchTabContent(viewModel, onNavigateToWebView)
                HubTab.BOOKMARKS -> BookmarksTabContent(uiState.bookmarks, onNavigateToWebView, viewModel::onBookmarkDeleted)
                HubTab.HISTORY -> HistoryTabContent(uiState.history, onNavigateToWebView)
                HubTab.SETTINGS -> {
                    // Navigate to settings and switch back to search tab so it's ready when user returns
                    LaunchedEffect(Unit) {
                        onNavigateToSettings()
                        viewModel.onTabSelected(HubTab.SEARCH)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Navigation Bar
            BottomNavBar(
                selectedTab = uiState.currentTab,
                onTabSelected = { viewModel.onTabSelected(it) }
            )
        }
    }
}

@Composable
fun HubAppHeader(onBack: () -> Unit, onUpgradeClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    // Streak animation for sparkly glimmer
    val streakOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "streak"
    )

    val proActive = remember { ThemeUtils.isPro(context) }
    val daysLeft = remember { ThemeUtils.getTrialDaysLeft(context) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = Color(0xFF101010).copy(alpha = 0.86f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFA0AAB0)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HUD",
                    color = Color(0xFF00C0BF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(20.dp)
                        .background(Color(0xFF3A3A3A))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "SEARCH",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            
            val badgeColor = if (BuildConfig.IS_PREMIUM) {
                Color(0xFFFFD700) // Gold for full pro
            } else if (daysLeft > 0) {
                Color(0xFF00C0BF) // Cyan for trial
            } else {
                Color(0xFFFFD700) // Gold for purchase prompt
            }
            
            val badgeText = if (BuildConfig.IS_PREMIUM) {
                "PRO"
            } else if (daysLeft > 0) {
                "PRO TRIAL ($daysLeft Days)"
            } else {
                "GET PRO"
            }
            
            Box(
                modifier = Modifier
                    .width(if (badgeText.length > 5) 120.dp else 60.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor)
                    .clickable { if (!BuildConfig.IS_PREMIUM) onUpgradeClick() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                // Sparkly Glimmer Overlay
                if (!BuildConfig.IS_PREMIUM) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val xPos = canvasWidth * streakOffset
                        
                        val brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.9f),
                                Color.White.copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            start = androidx.compose.ui.geometry.Offset(xPos - 30f, 0f),
                            end = androidx.compose.ui.geometry.Offset(xPos + 30f, canvasHeight)
                        )
                        drawRect(brush = brush)
                    }
                }

                Text(
                    text = badgeText,
                    color = Color(0xFF121212),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabContent(
    viewModel: DiscoveryHubViewModel,
    onNavigateToWebView: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Section
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Search anything.\nFind it everywhere.",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "One search. Multiple sources.",
            color = Color(0xFFA0AAB0),
            fontSize = 14.sp
        )

        // Primary Search Bar
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF121212).copy(alpha = 0.6f))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(28.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFFA0AAB0),
                modifier = Modifier.padding(start = 16.dp)
            )
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onQueryChanged(it) },
                placeholder = {
                    Text(
                        "Search images, inspiration, references...",
                        color = Color(0xFF606C75),
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF00C0BF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.onSearchPerformed(uiState.searchQuery)
                    onNavigateToWebView(viewModel.getDestinationUrl())
                })
            )
            IconButton(
                onClick = { 
                    viewModel.onSearchPerformed(uiState.searchQuery)
                    onNavigateToWebView(viewModel.getDestinationUrl()) 
                },
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF00C0BF))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Search",
                    tint = Color(0xFF121212)
                )
            }
        }

        // Source Selection Grid
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "CHOOSE A SOURCE",
            color = Color(0xFFA0AAB0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        val sources = listOf(
            SearchSource.ALL to Icons.Default.Language,
            SearchSource.GOOGLE_IMAGES to Icons.Default.Image,
            SearchSource.PINTEREST to Icons.Default.PushPin,
            SearchSource.X_TWITTER to Icons.Default.Close,
            SearchSource.MORE to Icons.Default.MoreHoriz
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sources) { (source, icon) ->
                SourceCard(
                    source = source,
                    icon = icon,
                    isSelected = uiState.selectedSource == source,
                    onClick = { viewModel.onSourceSelected(source) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "You can change your default source in settings.",
            color = Color(0xFF606C75),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarksTabContent(
    bookmarks: List<String>,
    onNavigateToWebView: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit
) {
    var bookmarkToDelete by remember { mutableStateOf<String?>(null) }

    if (bookmarkToDelete != null) {
        AlertDialog(
            onDismissRequest = { bookmarkToDelete = null },
            title = { Text("Delete Bookmark?") },
            text = { Text("Remove '${bookmarkToDelete}' from your bookmarks?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteBookmark(bookmarkToDelete!!)
                    bookmarkToDelete = null
                }) {
                    Text("DELETE", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkToDelete = null }) {
                    Text("CANCEL", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFA0AAB0)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Bookmarks",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        bookmarks.forEach { bookmark ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onNavigateToWebView(bookmark) },
                        onLongClick = { bookmarkToDelete = bookmark }
                    ),
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Text(
                    text = bookmark,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun HistoryTabContent(
    history: List<String>,
    onNavigateToWebView: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Recent History",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        history.forEach { item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { 
                        // Logic for history might vary, here we assume it's just strings
                        onNavigateToWebView("https://www.google.com/search?q=$item") 
                    },
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Text(
                    text = item,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SourceCard(
    source: SearchSource,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF00C0BF).copy(alpha = 0.1f) else Color(0xFF1E1E1E))
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF00C0BF) else Color(0xFF333333),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = source.displayName,
            tint = if (isSelected) Color(0xFF00C0BF) else Color(0xFFA0AAB0),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = source.displayName,
            color = if (isSelected) Color.White else Color(0xFFA0AAB0),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun BottomNavBar(
    selectedTab: HubTab,
    onTabSelected: (HubTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        color = Color(0xFF121212).copy(alpha = 0.9f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem("Search", Icons.Default.Search, selectedTab == HubTab.SEARCH) { onTabSelected(HubTab.SEARCH) }
            NavBarItem("Bookmarks", Icons.Default.Star, selectedTab == HubTab.BOOKMARKS) { onTabSelected(HubTab.BOOKMARKS) }
            NavBarItem("History", Icons.Default.History, selectedTab == HubTab.HISTORY) { onTabSelected(HubTab.HISTORY) }
            NavBarItem("Settings", Icons.Default.Settings, selectedTab == HubTab.SETTINGS) { onTabSelected(HubTab.SETTINGS) }
        }
    }
}

@Composable
fun NavBarItem(
    label: String, 
    icon: ImageVector, 
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFF00C0BF) else Color(0xFF606C75),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFF00C0BF) else Color(0xFF606C75),
            fontSize = 10.sp
        )
    }
}
