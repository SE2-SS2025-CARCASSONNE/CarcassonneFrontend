package at.se2_ss2025_gruppec.carcasonnefrontend

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.graphics.Brush
import androidx.navigation.compose.NavHost
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import at.se2_ss2025_gruppec.carcasonnefrontend.ApiClient.gameApi
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.AuthViewModel
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.Callbacks
import kotlinx.coroutines.launch
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import kotlinx.coroutines.delay
import org.json.JSONObject
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.*
import androidx.compose.foundation.shape.CircleShape
import kotlin.random.Random
import androidx.compose.foundation.layout.offset
import at.se2_ss2025_gruppec.carcasonnefrontend.model.dto.UserStatsDto
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Position
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Meeple
import at.se2_ss2025_gruppec.carcasonnefrontend.model.MeeplePosition
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.GameViewModel
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Tile
import at.se2_ss2025_gruppec.carcasonnefrontend.model.TileRotation
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.GameUiState
import kotlin.math.floor
import androidx.core.graphics.createBitmap
import at.se2_ss2025_gruppec.carcasonnefrontend.model.GamePhase
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Player
import java.util.UUID
import androidx.compose.foundation.BorderStroke
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        SoundManager.playMusic(this, R.raw.lobby_music)

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                val navController = rememberNavController()
                var userToken by remember { mutableStateOf(TokenManager.userToken) }

                val stompClient by remember(userToken) {
                    mutableStateOf(TokenManager.userToken?.let {
                        MyClient(object : Callbacks {
                            override fun onResponse(res: String) {
                                Toast.makeText(this@MainActivity, res, Toast.LENGTH_SHORT).show()
                            }
                        }).apply { connect() }
                    })
                }

                NavHost(navController = navController, startDestination = "landing") {
                    composable("landing") { LandingScreen(onStartTapped = {
                        navController.navigate("auth")
                    })}
                    composable("auth") { AuthScreen(onAuthSuccess = { jwtToken ->
                        TokenManager.userToken = jwtToken
                        userToken = jwtToken
                        navController.navigate("main")
                    })}
                    composable("main") { MainScreen(navController) }
                    composable("join_game") { JoinGameScreen(navController) }
                    composable("create_game") { CreateGameScreen(navController) }
                    composable("lobby/{gameId}") { backStackEntry ->
                        val gameId = backStackEntry.arguments?.getString("gameId") ?: ""
                        val playerName = TokenManager.loggedInUsername ?: "Unknown"

                        stompClient?.let {
                            LobbyScreen(gameId = gameId, playerName = playerName, stompClient = it, navController = navController)
                        } ?: run {
                            Log.e("MainActivity", "No stomp client available to show lobby")
                            Toast.makeText(this@MainActivity, "Connection not ready!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    composable("gameplay/{gameId}") { backStackEntry ->
                        val gameId = backStackEntry.arguments!!.getString("gameId")!!
                        val playerName = TokenManager.loggedInUsername!!
                        val client = stompClient
                            ?: throw IllegalStateException("Stomp client not initialized yet!")
                        GameplayScreen(
                            gameId      = gameId,
                            playerName  = playerName,
                            stompClient = client,
                            navController = navController
                        )
                    }
                }
                GlobalSoundMenu()
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        SoundManager.stopMusic()
    }
}
@Composable
fun GlobalSoundMenu() {
    var showVolumeControl by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableStateOf(SoundManager.volume) }
    var isMuted by remember { mutableStateOf(SoundManager.isMuted) }

    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = { showVolumeControl = !showVolumeControl },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(46.dp)
            )
        }

        if (showVolumeControl) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable { showVolumeControl = false }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 16.dp)
                    .background(Color(0xCC5A3A1A), shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Music Volume", color = Color.White)
                    Slider(
                        value = if (isMuted) 0f else volumeLevel,
                        onValueChange = {
                            volumeLevel = it
                            isMuted = it == 0f
                            SoundManager.setVolume(it)
                        },
                        valueRange = 0f..1f,
                        steps = 5,
                        modifier = Modifier.width(120.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Yellow,
                            inactiveTrackColor = Color.Gray
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                isMuted = true
                                SoundManager.mute()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A4A2A))
                        ) {
                            Text("Mute", color = Color.White)
                        }
                        Button(
                            onClick = {
                                isMuted = false
                                SoundManager.unmute()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A4A2A))
                        ) {
                            Text("Unmute", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LandingScreen(onStartTapped: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val floating by infiniteTransition.animateFloat( //Define floating animation behavior
        initialValue = 15f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val pulsating by infiniteTransition.animateFloat( //Define pulsating animation behaviour
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onStartTapped() } //Make entire screen clickable (= tap anywhere to continue)
    ) {
        BackgroundImage()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_pxart),
                contentDescription = null,
                modifier = Modifier
                    .size(380.dp)
                    .offset(y = floating.dp), //Apply floating animation to logo
            )

            Text(
                text = "tap to play",
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 65.dp)
                    .scale(pulsating) //Apply pulsating animation to text
            )
        }
    }
}

@Composable
fun AuthScreen(onAuthSuccess: (String) -> Unit, viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val username = viewModel.username
    val password = viewModel.password
    val isLoading = viewModel.isLoading

    LaunchedEffect(true) {
        viewModel.uiEvents.collect { message -> // Collect messages from ViewModel
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Box (modifier = Modifier.fillMaxSize()) {
        BackgroundImage()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 70.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedTextField( //Username input
                    value = username,
                    onValueChange = { viewModel.username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField( //Password input
                    value = password,
                    onValueChange = { viewModel.password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(50.dp))

                PixelArtButton( //Login button
                    label = if (isLoading) "Logging in..." else "Login", //Simple loading indicator
                    onClick = {
                        if (!isLoading) {
                            viewModel.login { jwt ->
                                TokenManager.loggedInUsername = viewModel.username
                                onAuthSuccess(jwt)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                PixelArtButton( //Register button
                    label = if (isLoading) "Registering..." else "Register",
                    onClick = {
                        if (!isLoading) {
                            viewModel.register()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun MainScreen(navController: NavController) {
    BackHandler(enabled = true) {}
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showStatsPopup by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<UserStatsDto?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundImage()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 170.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelArtButton(
                    label = "Join Game",
                    onClick = {
                        navController.navigate("join_game")
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                PixelArtButton(
                    label = "Create Game",
                    onClick = {
                        navController.navigate("create_game")
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                PixelArtButton(
                    label = "Statistics",
                    onClick = {
                        showStatsPopup = true
                        coroutineScope.launch {
                            try {
                                val token = TokenManager.userToken
                                if (token != null) {
                                    val bearer = "Bearer $token"
                                    val result = ApiClient.statsApi.getUserStats(bearer)
                                    stats = result
                                } else {
                                    Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to load stats", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        if (showStatsPopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showStatsPopup = false }
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .widthIn(max = 280.dp) // Adjusted width
                        .clickable(enabled = false) {}, // Prevents background click passing
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E3A59))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Player Statistics",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        stats?.let {
                            StatLine(label = "Games Played", value = it.totalGames.toString())
                            StatLine(label = "Games Won", value = it.totalWins.toString())
                            StatLine(label = "Win Ratio", value = "${(it.winRatio * 100).toInt()}%")
                            StatLine(label = "High Score", value = it.highScore.toString())
                        } ?: Text("Loading...", color = Color.White)

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tap anywhere to dismiss",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            modifier = Modifier.graphicsLayer(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White, fontSize = 16.sp)
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun JoinGameScreen(navController: NavController = rememberNavController()) {
    val context = LocalContext.current
    var gameId by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundImage()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 250.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = gameId,
                    onValueChange = { gameId = it },
                    label = { Text("Enter game ID") },
                    modifier = Modifier.width(220.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFF4C2),
                        textAlign = TextAlign.Center
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(50.dp))

                PixelArtButton(
                    label = "Join",
                    onClick = {
                        if (gameId.isBlank()) {
                            Toast.makeText(context, "Please enter a game ID!", Toast.LENGTH_SHORT).show()
                            return@PixelArtButton
                        }

                        val username = TokenManager.loggedInUsername
                        if (username.isNullOrBlank()) {
                            Toast.makeText(context, "No user logged in!", Toast.LENGTH_SHORT).show()
                            return@PixelArtButton
                        }

                        coroutineScope.launch {
                            try {
                                val token = TokenManager.userToken
                                    ?: throw IllegalStateException("Token is required, but was null!")

                                gameApi.getGame(
                                    token = "Bearer $token",
                                    gameId = gameId
                                )
                                navController.navigate("lobby/$gameId")
                            } catch (e: Exception) {
                                Log.e("JoinGame", "Exception during getGame: ${e.message}", e)
                                Toast.makeText(context, "Game not found or connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CreateGameScreen(navController: NavController) {
    var selectedPlayers by remember { mutableStateOf(2) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundImage()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 250.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(48.dp)) // Added some spacing since we removed the input field

                Text(
                    text = "Select Player Count:",
                    fontSize = 20.sp,
                    color = Color(0xFFFFF4C2),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row {
                    (2..4).forEach { count ->
                        Button(
                            onClick = { selectedPlayers = count },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedPlayers == count) Color(0xFF8B4513) else Color(0x995A3A1A)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text("$count", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(50.dp))

                PixelArtButton(
                    label = "Create",
                    onClick = {
                        val hostName = TokenManager.loggedInUsername ?: return@PixelArtButton

                        coroutineScope.launch {
                            try {
                                //Retrieve token from Singleton and append it to API call
                                val token = TokenManager.userToken ?: throw IllegalStateException("Token is required, but was null!")
                                val response = gameApi.createGame(
                                    token = "Bearer $token",
                                    request = CreateGameRequest(playerCount = selectedPlayers, hostName = hostName)
                                )
                                Toast.makeText(context, "Game created! ID: ${response.gameId}", Toast.LENGTH_LONG).show()
                                navController.navigate("lobby/${response.gameId}")

                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun LobbyScreen(gameId: String, playerName: String, stompClient: MyClient, navController: NavController) {
    val backStackEntry = remember(navController, gameId) {
        navController.getBackStackEntry("lobby/$gameId")
    }
    val viewModel: GameViewModel = viewModel(backStackEntry)

    val players = remember { mutableStateListOf(playerName) }
    val hostName = remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    fun handleLobbyMessage(message: String) {
        viewModel.handleWebSocketMessage(message)

        val json = JSONObject(message)
        when (json.getString("type")) {
            "game_started" -> {
                Handler(Looper.getMainLooper()).post {
                    navController.navigate("gameplay/$gameId")
                }
            }
            "player_joined" -> {
                val playerArray = json.getJSONArray("players")
                val host = json.optString("host", "")
                Handler(Looper.getMainLooper()).post {
                    players.clear()
                    for (i in 0 until playerArray.length()) {
                        players.add(playerArray.getString(i))
                    }
                    hostName.value = host
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (!stompClient.isConnected()) delay(100)
        stompClient.listenOn("/topic/game/$gameId") { handleLobbyMessage(it) }
        stompClient.listenOn("/user/queue/private") { handleLobbyMessage(it) }
        delay(700)
        try {
            stompClient.sendJoinGame(gameId, playerName)
        } catch (e: Exception) {
            Toast.makeText(context, "Join failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BackgroundImage()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 250.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Game ID block
            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, Color(0xFFFFF4C2)),
                colors = CardDefaults.cardColors(containerColor = Color(0x66000000)),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(0.6f)
                    .height(70.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Game ID: $gameId",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF4C2)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(gameId))
                        Toast.makeText(context, "Copied game ID!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Game ID",
                            tint = Color.White
                        )
                    }
                }
            }

            Text(
                text = "Waiting for players to join...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            val maxPlayers = 4
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until maxPlayers) {
                    val playerNameInList = players.getOrNull(i) ?: "Empty Slot"
                    val displayName = if (playerNameInList.trim() == playerName.trim()) "You" else playerNameInList

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFF4C2)),
                        colors = CardDefaults.cardColors(containerColor = Color(0x995A3A1A)),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(50.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            if (hostName.value == playerName && players.size > 1) {
                PixelArtButton(
                    label = "Start Game",
                    onClick = {
                        stompClient.sendStartGame(gameId, playerName)
                    }
                )
            }
        }
    }
}

@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun GameplayScreen(gameId: String, playerName: String, stompClient: MyClient, navController: NavController) {
    BackHandler(enabled = true) {}
    val context = LocalContext.current
    val showEndGameDialog = remember { mutableStateOf(false) }
    val winner = remember { mutableStateOf("") }
    val scores = remember { mutableStateListOf<Pair<String, Int>>() }

    val backStackEntry = remember(navController, gameId) {
        navController.getBackStackEntry("lobby/$gameId")
    }
    val viewModel: GameViewModel = viewModel(backStackEntry)

    LaunchedEffect(gameId) {
        viewModel.setWebSocketClient(stompClient)
        viewModel.setJoinedPlayer(playerName)

        while (!stompClient.isConnected()) delay(100)

        stompClient.listenOn("/topic/game/$gameId") { msg ->
            val json = JSONObject(msg)
            if (json.getString("type") == "game_over") {
                val winnerName = json.getString("winner")
                val scoreArray = json.getJSONArray("scores")
                val parsedScores = mutableListOf<Pair<String, Int>>()
                for (i in 0 until scoreArray.length()) {
                    val entry = scoreArray.getJSONObject(i)
                    parsedScores.add(entry.getString("player") to entry.getInt("score"))
                }
                winner.value = winnerName
                scores.clear()
                scores.addAll(parsedScores)
                showEndGameDialog.value = true
            }
        }
    }

    //Music switch (only once on enter)
    LaunchedEffect(Unit) {
        SoundManager.playMusic(context, R.raw.gameplay_music)
    }

    val uiState by viewModel.uiState.collectAsState()

    // Error-Toast abonnieren
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { errorMsg ->
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    // Phase-Flags (TILE-, MEEPLE- oder SCORING-Phase)
    // uiState kann Loading / Error / Success sein → deshalb erst casten
    val phase = (uiState as? GameUiState.Success)
        ?.gameState
        ?.gamePhase

    val tileMode   = phase == GamePhase.TILE_PLACEMENT
    val meepleMode = phase == GamePhase.MEEPLE_PLACEMENT

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundImage()

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(35.dp))

            PlayerRow(viewModel)

            Spacer(modifier = Modifier.height(5.dp))

            TileBackRow(viewModel, gameId, playerName)

            Spacer(modifier = Modifier.height(20.dp))

            if (uiState is GameUiState.Success) {
                val gameState = (uiState as GameUiState.Success).gameState
                val placedTiles = gameState.board.values.toList()
                val meeples = gameState.meeples
                val players = viewModel.players

                PannableTileGrid(
                    tiles = placedTiles,
                    meeples = meeples,
                    players = players,
                    tileMode = tileMode,
                    meepleMode  = meepleMode,
                    onTileClick = { x, y -> if (!tileMode) return@PannableTileGrid

                        val pos = Position(x, y)
                        if (viewModel.isValidPlacement(pos)) {
                            viewModel.placeTileAt(pos, gameId)
                        } else {
                            Log.e("Frontend Guard", "Invalid tile placement at $pos — no adjacent tiles or already occupied")
                        }
                    },

                    onMeepleClick = { x, y, zone ->
                        if (!meepleMode) return@PannableTileGrid // wir sind nicht im Meeple-Modus
                        Log.d("GameplayScreen", "MeepleClick ausgelöst: x=$x, y=$y, zone=$zone") // Debug-Log für zone!

                        // Tile unter (x,y) im Board suchen
                        val clickedPos  = Position(x, y)
                        val targetTile = gameState.board[clickedPos] ?: run {
                            Log.e("GameplayScreen", "No tile at $clickedPos → kein Meeple möglich")
                            return@PannableTileGrid
                        }

                        // Einmalige Meeple-ID erzeugen
                        val meepleId = UUID.randomUUID().toString()
                        val playerId = TokenManager.loggedInUsername ?: "unknown"

                        Log.d("GameplayScreen",
                            "Meeple wird platziert → game=$gameId, player=$playerId, " +
                                    "tile=${targetTile.id}, zone=${zone.name}")

                        viewModel.placeMeeple(
                            gameId  = gameId,
                            playerId= playerId,
                            meepleId= meepleId,
                            tileId  = targetTile.id,
                            position= zone.name
                        )
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            BottomScreenBar(viewModel, gameId)

            CheatShakeListener(
                enabled = phase == GamePhase.TILE_PLACEMENT &&
                        viewModel.currentTile.value != null &&
                        playerName == viewModel.currentPlayerId.value,
            ) {
                viewModel.cheatRedraw(gameId)
            }

            if (showEndGameDialog.value) {
                ConfettiAnimation(visible = true)

                LaunchedEffect(Unit) {
                    SoundManager.playMusic(context, R.raw.endgame_music1)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF2E1C0C), Color(0xFF4E342E))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(32.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.bg_pxart),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer(alpha = 0.15f)
                    )

                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {
                            Button(
                                onClick = {
                                    showEndGameDialog.value = false
                                    SoundManager.playMusic(context, R.raw.lobby_music)
                                    navController.popBackStack("main", false)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8D6E63),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Return to Main Menu", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "\uD83C\uDFC6 Game Over",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = Color(0xFFFFD700)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Winner: ${winner.value}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        },
                        text = {
                            Column(horizontalAlignment = Alignment.Start) {
                                Spacer(Modifier.height(12.dp))
                                scores.sortedByDescending { it.second }.forEachIndexed { index, (name, score) ->
                                    Text(
                                        text = "${index + 1}. $name – $score points",
                                        fontSize = 16.sp,
                                        color = if (name == winner.value) Color(0xFFFFD700) else Color.White,
                                        fontWeight = if (name == winner.value) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        },
                        containerColor = Color(0xFF3E2723),
                        titleContentColor = Color.White,
                        textContentColor = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerRow(viewModel: GameViewModel) {
    val players = viewModel.players
    val currentPlayerId by viewModel.currentPlayerId

    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        players.forEachIndexed { idx, p ->
            val isCurrent = p.id == currentPlayerId

            // 1) Meeple-Drawable-Name per Index
            val meepleName = when (idx.coerceIn(0..3)) {
                0 -> "meeple_blu"
                1 -> "meeple_red"
                2 -> "meeple_grn"
                else -> "meeple_yel"
            }
            // 2) Ressource holen
            val resId = remember(meepleName) {
                context.resources.getIdentifier(meepleName, "drawable", context.packageName)
            }

            // 3) Text-Tint abhängig ob aktuell
            val tint = if (isCurrent) Color.Green else Color.White

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(4.dp)
            ) {
                // statt Icons.Default.Person
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = "Meeple von ${p.id}",
                    modifier = Modifier.size(35.dp)
                )

                Text(
                    text = p.id,
                    color = tint,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${p.score}P",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

            }
        }
    }
}

@Composable
fun TileBackRow(viewModel: GameViewModel, gameId: String, playerId: String) {
    val deckRemaining by viewModel.deckRemaining.collectAsState(initial = 71)

    val base = deckRemaining / 4
    val extra = deckRemaining % 4
    val piles = List(4) { index ->
        base + if (index < extra) 1 else 0
        base + if (index < extra) 1 else 0
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        piles.forEachIndexed { index, remaining ->
            TileBackButton(
                remaining = remaining,
                isEnabled = remaining > 0,
                onClick = { viewModel.requestTileFromBackend(gameId, playerId) }
            )
        }
    }
}

@Composable
fun TileBackButton(
    remaining: Int,
    onClick: () -> Unit,
    backgroundRes: Int = R.drawable.tile_back,
    isEnabled: Boolean
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clickable(enabled = isEnabled) { onClick() }
    ) {
        Image(
            painter = painterResource(id = backgroundRes),
            contentDescription = "Tile back image",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )

        Text(
            text = "$remaining",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isEnabled) Color.White else Color.Red,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun PannableTileGrid(
    tiles:       List<Tile>,
    meeples:     List<Meeple>,
    players:     List<Player>,
    tileMode:    Boolean,
    meepleMode:  Boolean,
    onTileClick:   (Int, Int) -> Unit,
    onMeepleClick: (Int, Int, MeeplePosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    /* ----------------------------------------------------------
       Grund-Größen und States
       ---------------------------------------------------------- */
    val tileSize = 100.dp
    val tileSizePx = with(LocalDensity.current) { tileSize.toPx() }
    val context    = LocalContext.current

    val scale   = remember { mutableFloatStateOf(1f) }
    val offsetX = remember { mutableFloatStateOf(0f) }
    val offsetY = remember { mutableFloatStateOf(0f) }
    val imageCache = remember { mutableMapOf<String, ImageBitmap>() }

    /* ----------------------------------------------------------
       Gesten-Handling
       ---------------------------------------------------------- */
    val gestureModifier = Modifier
        /* ❶ Pan & Zoom – immer aktiv */
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale.floatValue   *= zoom
                offsetX.floatValue += pan.x
                offsetY.floatValue += pan.y
            }
        }
        /* ❷ Tap-Handling – hängt vom Modus ab */
        .pointerInput(tileMode, meepleMode) {
            detectTapGestures { offset ->
                val scaled = tileSizePx * scale.floatValue
                val x = floor((offset.x - offsetX.floatValue) / scaled).toInt()
                val y = floor((offset.y - offsetY.floatValue) / scaled).toInt()

                /* ---------- TILE ---------- */
                if (tileMode) onTileClick(x, y)

                /* ---------- MEEPLE ---------- */
                if (meepleMode) {
                    val seg = scaled / 3f
                    val localX = offset.x - (x * scaled + offsetX.floatValue)
                    val localY = offset.y - (y * scaled + offsetY.floatValue)

                    val zone = when {
                        localX <  seg         && localY in  seg..2*seg -> MeeplePosition.W
                        localX >  2*seg       && localY in  seg..2*seg -> MeeplePosition.E
                        localY <  seg         && localX in  seg..2*seg -> MeeplePosition.N
                        localY >  2*seg       && localX in  seg..2*seg -> MeeplePosition.S
                        localX in seg..2*seg  && localY in seg..2*seg -> MeeplePosition.C
                        else -> null
                    }
                    zone?.let { onMeepleClick(x, y, it) }
                }
            }
        }

    /* ----------------------------------------------------------
       Zeichnen
       ---------------------------------------------------------- */
    Box(
        modifier = modifier
            .clipToBounds()
            .then(gestureModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaledSize = (tileSizePx * scale.floatValue).coerceAtLeast(1f)
            val seg        = scaledSize / 3f

            /* ---------- 1) Tiles ---------- */
            tiles.forEach { tile ->
                val pos  = tile.position ?: return@forEach
                val left = pos.x * scaledSize + offsetX.floatValue
                val top  = pos.y * scaledSize + offsetY.floatValue

                val baseName  = tile.id.substringBeforeLast("-").replace("-", "_")
                val derivedId = context.resources.getIdentifier(baseName, "drawable", context.packageName)
                val resId     = tile.drawableRes?.takeIf { it != 0 } ?: derivedId   // <- Safe-Call wieder drin
                if (resId == 0) return@forEach

                val img = imageCache.getOrPut(baseName) {
                    val dr  = ContextCompat.getDrawable(context, resId)!!
                    val bmp = drawableToBitmap(dr, scaledSize.toInt(), scaledSize.toInt())
                    bmp.asImageBitmap()
                }

                withTransform({
                    translate(left, top)
                    rotate(tile.tileRotation.degrees.toFloat(), pivot = Offset(scaledSize/2, scaledSize/2))
                }) {
                    drawImage(img, dstSize = IntSize(scaledSize.toInt(), scaledSize.toInt()))
                }
            }

            /* ---------- 2) Meeples ---------- */
            meeples.forEach { meeple ->
                val px = meeple.x * scaledSize + offsetX.floatValue
                val py = meeple.y * scaledSize + offsetY.floatValue

                val (cx, cy) = when (meeple.position) {
                    MeeplePosition.W -> seg*0.5f to seg*1.5f
                    MeeplePosition.E -> seg*2.5f to seg*1.5f
                    MeeplePosition.N -> seg*1.5f to seg*0.5f
                    MeeplePosition.S -> seg*1.5f to seg*2.5f
                    MeeplePosition.C -> seg*1.5f to seg*1.5f
                    null -> return@forEach
                }

                val idx = players.indexOfFirst { it.id == meeple.playerId }
                    .let { if (it >= 0) it.coerceIn(0..3) else 0 }

                val drawableName = when (idx) {
                    0 -> "meeple_blu"
                    1 -> "meeple_red"
                    2 -> "meeple_grn"
                    else -> "meeple_yel"
                }

                val rid = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
                if (rid == 0) return@forEach
                val dr = ContextCompat.getDrawable(context, rid) ?: return@forEach

                val dstPx    = (scaledSize * 0.3f).toInt()         // 30 % der Kachel
                val cacheKey = "${drawableName}_$dstPx"

                val bmp = imageCache.getOrPut(cacheKey) {
                    drawableToBitmap(dr, dstPx, dstPx).asImageBitmap()
                }

                drawImage(
                    image     = bmp,
                    dstOffset = IntOffset(
                        ((px + cx) - dstPx / 2).toInt(),
                        ((py + cy) - dstPx / 2).toInt()
                    ),
                    dstSize   = IntSize(dstPx, dstPx)
                )
            }
        }
    }
}

fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
    if (drawable is BitmapDrawable) return drawable.bitmap
    val bitmap = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap
}


@Composable
fun BottomScreenBar(viewModel: GameViewModel, gameId: String) {
    val context = LocalContext.current
    val tile = viewModel.currentTile.value
    val players = viewModel.players
    val localPlayerId = TokenManager.loggedInUsername
    val localPlayer = players.find { it.id == localPlayerId }

    val idx = players.indexOfFirst { it.id == localPlayerId }
        .let { if (it >= 0) it.coerceIn(0..3) else 0 }

    val meepleDrawableName = when (idx) {
        0 -> "meeple_blu"
        1 -> "meeple_red"
        2 -> "meeple_grn"
        else -> "meeple_yel"
    }

    val meepleResId = remember(meepleDrawableName) {
        context.resources.getIdentifier(meepleDrawableName, "drawable", context.packageName)
    }

    val noMeepleDrawableName = when (idx) {
        0 -> "nomeeple_blu"
        1 -> "nomeeple_red"
        2 -> "nomeeple_grn"
        else -> "nomeeple_yel"
    }

    val noMeepleResId = remember(idx) {
        context.resources.getIdentifier(noMeepleDrawableName, "drawable", context.packageName)
    }

    val remainingMeeples by viewModel.remainingMeeples.collectAsState()
    val isMeeplePlacementActive = viewModel.isMeeplePlacementActive.collectAsState()
    Log.d("MeeplePlacement", "UI State: ${isMeeplePlacementActive.value}") //TODO Mike dann wieder entfernen!

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp) // Höhe der BottomScreenBar bleibt erhalten
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly // Gleichmäßige Verteilung der Elemente
        ) {
            // Linke Seite: Meeple mit Zahl darüber
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = meepleResId),
                    contentDescription = "Meeple setzen",
                    modifier = Modifier
                        .size(if (isMeeplePlacementActive.value) 85.dp else 65.dp)
                        .clickable { viewModel.setMeeplePlacement(!isMeeplePlacementActive.value) }
                )

                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${remainingMeeples[TokenManager.loggedInUsername] ?: 7}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Mitte: Das Tile-Bild bleibt exakt zentriert
            Box(
                modifier = Modifier.height(170.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (tile != null) {
                    DrawTile(tile, viewModel)
                } else {
                    Spacer(modifier = Modifier.size(135.dp))
                }
            }

            // Rechte Seite: No-Meeple-Symbol + Punkt-Badge (opt. mit Spacer für Balance)
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = noMeepleResId),
                        contentDescription = "Meeple nicht setzen",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(65.dp)
                            .clickable {
                                viewModel.skipMeeple(gameId)
                            }
                    )

                    Spacer(modifier = Modifier.height(13.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 25.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${localPlayer?.score ?: 0}P",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CheatShakeListener(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val sensorMgr = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelSensor = remember { sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val lastTrigger = remember { mutableLongStateOf(0L) }
    val threshold = 12f
    val minInterval = 1500

    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                val gX = ev.values[0]; val gY = ev.values[1]; val gZ = ev.values[2]
                val gForce = sqrt(gX*gX + gY*gY + gZ*gZ) - SensorManager.GRAVITY_EARTH
                if (gForce > threshold) {
                    val now = System.currentTimeMillis()
                    if (now - lastTrigger.longValue > minInterval) {
                        lastTrigger.longValue = now
                        onShake()
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensorMgr.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorMgr.unregisterListener(listener) }
    }
}

@Composable
fun BackgroundImage() {
    Image(
        painter = painterResource(id = R.drawable.bg_pxart),
        contentDescription = null,
        contentScale = ContentScale.Crop, // Maintains aspect ratio & prevents warping
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun PixelArtButton(
    label: String,
    onClick: () -> Unit,
    backgroundRes: Int = R.drawable.button_pxart
) {
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(100.dp)
            .clickable { onClick() }
    ) {
        Image(
            painter = painterResource(id = backgroundRes),
            contentDescription = "Pixel button background",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )

        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = Color(0xFFFFF4C2),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

//Singleton TokenManager to store JWT
object TokenManager {
    var userToken: String? = null
    var loggedInUsername: String? = null
}

//Custom parser to parse HTTP error messages returned by backend
fun parseErrorMessage(body: String?): String {
    return try {
        val json = JSONObject(body ?: "")
        json.getString("message")
    } catch (_: Exception) {
        "Unexpected error!"
    }
}

@Composable
fun DrawTile(tile: Tile, viewModel: GameViewModel) {
    val context = LocalContext.current

    val baseId = tile.id.split("-").take(2).joinToString("-") // e.g. "tile-b-1" → "tile-b"
    val drawableName = baseId.replace("-", "_") // -> "tile"

    val drawableId = remember(drawableName) {
        context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    }

    if (drawableId != 0) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = tile.id,
            modifier = Modifier
                .size(135.dp)
                .graphicsLayer(
                    rotationZ = when (tile.tileRotation) {
                        TileRotation.NORTH -> 0f
                        TileRotation.EAST -> 90f
                        TileRotation.SOUTH -> 180f
                        TileRotation.WEST -> 270f
                    }
                )
                .clickable(onClick = { viewModel.rotateCurrentTile() })
        )
    } else {
        Box(
            modifier = Modifier
                .size(135.dp)
                .background(Color.Red),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "?", color = Color.White)
        }
    }
}

@Composable
fun ConfettiAnimation(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val confettiList = remember { mutableStateListOf<ConfettiParticle>() }

    LaunchedEffect(visible) {
        while (visible) {
            repeat(8) {
                confettiList.add(
                    ConfettiParticle(
                        x = Random.nextInt(0, screenWidth.value.toInt()).dp,
                        color = Color(
                            red = Random.nextFloat(),
                            green = Random.nextFloat(),
                            blue = Random.nextFloat()
                        )
                    )
                )
            }
            delay(200)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        confettiList.forEach { particle ->
            ConfettiFallingItem(
                x = particle.x,
                color = particle.color,
                screenHeight = screenHeight,
                onFinished = { confettiList.remove(particle) }
            )
        }
    }
}

@Composable
fun ConfettiFallingItem(
    x: Dp,
    color: Color,
    screenHeight: Dp,
    onFinished: () -> Unit
) {
    val yOffset = remember { Animatable(-10f) }
    LaunchedEffect(Unit) {
        yOffset.animateTo(
            targetValue = screenHeight.value + 20f,
            animationSpec = tween(
                durationMillis = 2500 + Random.nextInt(0, 1000),
                easing = LinearEasing
            )
        )
        onFinished()
    }
    Box(
        modifier = Modifier
            .offset(x = x, y = yOffset.value.dp)
            .size(8.dp)
            .background(color = color, shape = CircleShape)
    )
}

data class ConfettiParticle(
    val x: Dp,
    val color: Color
)
