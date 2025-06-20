package at.se2_ss2025_gruppec.carcasonnefrontend

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.navigation.compose.NavHost
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.drawBehind
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
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Position
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Meeple
import at.se2_ss2025_gruppec.carcasonnefrontend.model.MeeplePosition
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.GameViewModel
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Tile
import at.se2_ss2025_gruppec.carcasonnefrontend.model.TileRotation
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.GameUiState
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.bottomColor
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.leftColor
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.rightColor
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.topColor
import kotlin.math.floor
import androidx.core.graphics.createBitmap
import at.se2_ss2025_gruppec.carcasonnefrontend.model.GamePhase
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Player
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        navController.navigate("auth") {
                            popUpTo("landing") { inclusive = true }
                        }
                    })}
                    composable("auth") { AuthScreen(onAuthSuccess = { jwtToken ->
                        TokenManager.userToken = jwtToken
                        userToken = jwtToken
                        navController.navigate("main") {
                            popUpTo("auth") { inclusive = true }
                        }
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
    var volumeLevel by remember { mutableStateOf(0.5f) }
    var isMuted by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Top-right floating gear icon
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
            // Transparent full-screen dismiss layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable { showVolumeControl = false } // click outside = close
            )

            // Settings popup on top
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
                                SoundManager.setVolume(0f)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A4A2A))
                        ) {
                            Text("Mute", color = Color.White)
                        }
                        Button(
                            onClick = {
                                isMuted = false
                                SoundManager.setVolume(volumeLevel)
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
fun TileView(tile: Tile) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .border(2.dp, Color.White)
            .drawBehind {
                val width = size.width
                val height = size.height
                val edge = 10f

                drawRect(color = tile.topColor(), topLeft = Offset(0f, 0f), size = Size(width, edge))
                drawRect(color = tile.rightColor(), topLeft = Offset(width - edge, 0f), size = Size(edge, height))
                drawRect(color = tile.bottomColor(), topLeft = Offset(0f, height - edge), size = Size(width, edge))
                drawRect(color = tile.leftColor(), topLeft = Offset(0f, 0f), size = Size(edge, height))

                drawCircle(Color.Black, radius = 4f, center = Offset(width / 2, height / 2))
            }
    )
}

@Composable
fun AuthScreen(onAuthSuccess: (String) -> Unit, viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    var username = viewModel.username
    var password = viewModel.password
    var isLoading = viewModel.isLoading

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
                .padding(bottom = 100.dp),
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
    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundImage()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 320.dp),
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
            }
        }
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
                .padding(bottom = 285.dp),
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
                            Toast.makeText(context, "Please enter a game ID", Toast.LENGTH_SHORT).show()
                            return@PixelArtButton
                        }

                        val username = TokenManager.loggedInUsername
                        if (username.isNullOrBlank()) {
                            Toast.makeText(context, "No user logged in", Toast.LENGTH_SHORT).show()
                            return@PixelArtButton
                        }

                        coroutineScope.launch {
                            try {
                                val token = TokenManager.userToken
                                    ?: throw IllegalStateException("Token is required, but was null!")

                                val gameState = gameApi.getGame(
                                    token = "Bearer $token",
                                    gameId = gameId
                                )
                                val playerCount = gameState.players.size.coerceAtLeast(2)
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
                .padding(bottom = 320.dp),
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

                Spacer(modifier = Modifier.height(24.dp))

                PixelArtButton(
                    label = "Create",
                    onClick = {
                        val hostName = TokenManager.loggedInUsername ?: return@PixelArtButton.also {
                            Toast.makeText(context, "No user logged in", Toast.LENGTH_SHORT).show()
                        }

                        coroutineScope.launch {
                            try {
                                //Retrieve token from Singleton and append it to API call
                                val token = TokenManager.userToken ?: throw IllegalStateException("Token is required, but was null!")
                                val response = gameApi.createGame(
                                    token = "Bearer $token",
                                    request = CreateGameRequest(playerCount = selectedPlayers, hostName = hostName)
                                )
                                Toast.makeText(context, "Game Created! ID: ${response.gameId}", Toast.LENGTH_LONG).show()
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
        Log.d("WebSocket", "Received message: $message")
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
                Log.d("LobbyScreen", "Parsed host=$host vs player=$playerName")

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
        while (!stompClient.isConnected()) {
            delay(100)
        }
        //Subscribe to both public and private channels
        stompClient.listenOn("/topic/game/$gameId") { handleLobbyMessage(it) }
        stompClient.listenOn("/user/queue/private") { handleLobbyMessage(it) }
        delay(700) //In order to give time for subscription to happen
        //Send join AFTER subscriptions
        try {
            Log.d("LobbyScreen", "Sending join_game for $playerName to $gameId")
            stompClient.sendJoinGame(gameId, playerName)

        } catch (e: Exception) {
            Log.e("LobbyScreen", "Failed to send join_game: ${e.message}")
            Toast.makeText(context, "Failed to join game: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        BackgroundImage()

        Text(
            text = "Lobby",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 2.sp,
            color = Color(0xFFFFF4C2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 65.dp)
                .padding(start = 150.dp)
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .border(2.dp, Color(0xFFFFF4C2), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .background(Color(0x99000000))
                ) {
                    Text(
                        text = "Game ID: $gameId",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF4C2)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(gameId))
                    Toast.makeText(context, "Copied Game ID", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Game ID",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Waiting for players...",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))

            val maxPlayers = 4

            for (i in 0 until maxPlayers) {
                val playerNameInList = players.getOrNull(i) ?: "Empty Slot"
                val displayName =
                    if (playerNameInList.trim() == playerName.trim()) "You" else playerNameInList

                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(48.dp)
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x995A3A1A),
                        disabledContainerColor = Color(0x995A3A1A),
                        disabledContentColor = Color.White
                    )
                ) {
                    Text(
                        text = displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(68.dp))

            if (hostName.value == playerName) {
                Button(
                    onClick = {
                        Toast.makeText(context, "Game starting...", Toast.LENGTH_SHORT).show()
                        stompClient.sendStartGame(gameId,playerName)
                    },
                    modifier = Modifier
                        .width(200.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC5A3A1A))
                ) {
                    Text(
                        text = "Start Game",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun GameplayScreen(gameId: String, playerName: String, stompClient: MyClient, navController: NavController) {

    val backStackEntry = remember(navController, gameId) {
        navController.getBackStackEntry("lobby/$gameId")
    }
    val viewModel: GameViewModel = viewModel(backStackEntry)

    LaunchedEffect(gameId) {
        viewModel.setWebSocketClient(stompClient)
        viewModel.setJoinedPlayer(playerName)
        viewModel.subscribeToGame(gameId)
        viewModel.subscribeToPrivate()
        viewModel.joinGame(gameId, playerName)
    }

    val uiState by viewModel.uiState.collectAsState()

    // Phase-Flags (TILE-, MEEPLE- oder SCORING-Phase)
    // uiState kann Loading / Error / Success sein → deshalb erst casten
    val phase = (uiState as? GameUiState.Success)
        ?.gameState
        ?.gamePhase

    val tileMode   = phase == GamePhase.TILE_PLACEMENT
    val meepleMode = phase == GamePhase.MEEPLE_PLACEMENT
    val scoring    = phase == GamePhase.SCORING

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundImage()

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(40.dp))

            PlayerRow(viewModel)

            Spacer(modifier = Modifier.height(20.dp))

            TileBackRow(viewModel, gameId, playerName)

            Spacer(modifier = Modifier.height(20.dp))

            if (uiState is GameUiState.Success) {
                val gameState = (uiState as GameUiState.Success).gameState
                val placedTiles = gameState.board.values.toList()
                val meeples     = gameState.meeples
                val players = gameState.players

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

                    // Meeple platzieren
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

        }
    }
}

@Composable
fun PlayerRow(viewModel: GameViewModel) {
    val players = viewModel.players
    val currentPlayerId by viewModel.currentPlayerId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        players.forEach { p ->
            val isCurrent = p.id == currentPlayerId
            val tint = if (isCurrent) Color.Green else Color.White

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Player ${p.id}",
                    tint = tint,
                    modifier = Modifier.size(30.dp)
                )
                Text(text = p.id,
                    color = tint,
                    fontSize = 12.sp
                )
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

@SuppressLint("DiscouragedApi")
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
                scale.value   *= zoom
                offsetX.value += pan.x
                offsetY.value += pan.y
            }
        }
        /* ❷ Tap-Handling – hängt vom Modus ab */
        .pointerInput(tileMode, meepleMode) {
            detectTapGestures { offset ->
                val scaled = tileSizePx * scale.value
                val x = floor((offset.x - offsetX.value) / scaled).toInt()
                val y = floor((offset.y - offsetY.value) / scaled).toInt()

                /* ---------- TILE ---------- */
                if (tileMode) onTileClick(x, y)

                /* ---------- MEEPLE ---------- */
                if (meepleMode) {
                    val seg = scaled / 3f
                    val localX = offset.x - (x * scaled + offsetX.value)
                    val localY = offset.y - (y * scaled + offsetY.value)

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
            val scaledSize = (tileSizePx * scale.value).coerceAtLeast(1f)
            val seg        = scaledSize / 3f

            /* ---------- 1) Tiles ---------- */
            tiles.forEach { tile ->
                val pos  = tile.position ?: return@forEach
                val left = pos.x * scaledSize + offsetX.value
                val top  = pos.y * scaledSize + offsetY.value

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
                val px = meeple.x * scaledSize + offsetX.value
                val py = meeple.y * scaledSize + offsetY.value

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
                    1 -> "meeple_grn"
                    2 -> "meeple_red"
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
    val tile = viewModel.currentTile.value //TODO: Mike oder doch collectAsState?
    val currentPlayerId = viewModel.currentPlayerId.value
    val players = viewModel.players
    val currentPlayer = players.find { it.id == currentPlayerId }
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
                    painter = painterResource(id = R.drawable.meeple_blu),
                    contentDescription = "Meeple setzen",
                    modifier = Modifier
                        .size(if (isMeeplePlacementActive.value) 85.dp else 65.dp)
                        .clickable { viewModel.setMeeplePlacement(!isMeeplePlacementActive.value) }
                )

                Text(
                    text = "${remainingMeeples[TokenManager.loggedInUsername] ?: 7}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Mitte: Das Tile-Bild bleibt exakt zentriert
            Box(
                modifier = Modifier.height(170.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                tile?.let {
                    DrawTile(it, viewModel)
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
                        painter = painterResource(id = R.drawable.meeple_no),
                        contentDescription = "Meeple nicht setzen",
                        contentScale = ContentScale.FillBounds,
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
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 25.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${currentPlayer?.score ?: 0}P",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
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

@SuppressLint("DiscouragedApi")
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
