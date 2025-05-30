package at.se2_ss2025_gruppec.carcasonnefrontend

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import at.se2_ss2025_gruppec.carcasonnefrontend.ApiClient.gameApi
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.AuthViewModel
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.Callbacks
import kotlinx.coroutines.launch
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import at.se2_ss2025_gruppec.carcasonnefrontend.SoundManager
import kotlinx.coroutines.delay
import org.json.JSONObject
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.GameViewModel
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.Tile
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.TileRotation
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.bottomColor
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.getDrawableNameForTile
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.leftColor
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.rightColor
import at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel.topColor

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
                        val gameId = backStackEntry.arguments?.getString("gameId") ?: ""
                        GameplayScreen(gameId)
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
                Spacer(modifier = Modifier.height(16.dp))

                PixelArtButton( //Just for development
                    label = "Skip (for devs)",
                    onClick = {
                        onAuthSuccess("mock-jwt")
                    }
                )
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

@Composable
fun LobbyScreen(gameId: String, playerName: String, stompClient: MyClient, navController: NavController) {
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
                Log.d("LobbyScreen", "Parsed host=$host vs player=$playerName") // ⬅️ Add this

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
                        stompClient.sendStartGame(gameId)
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

@Preview(showBackground = true)
@Composable
fun GameplayScreenPreview() {
    GameplayScreen("123")
}

@Composable
fun GameplayScreen(gameId: String) {
    val viewModel: GameViewModel = viewModel()
    val currentTile by viewModel.currentTile

    LaunchedEffect(Unit) {
        viewModel.subscribeToGame(gameId)
    }


    Box(modifier = Modifier.fillMaxSize()) {
        print(gameId)

        BackgroundImage()


        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(40.dp))

            PlayerRow()

            Spacer(modifier = Modifier.height(20.dp))

            TileBackRow()

            Spacer(modifier = Modifier.height(20.dp))

            val tiles = remember { // Just for showcasing UI, delete later
                listOf(
                    TileData(x = 0, y = 0, drawableRes = R.drawable.tile_a),
                    TileData(x = 1, y = 0, drawableRes = R.drawable.tile_b),
                    TileData(x = 2, y = 0, drawableRes = R.drawable.tile_c),
                    TileData(x = 4, y = 0, drawableRes = R.drawable.tile_d),
                    TileData(x = 0, y = 1, drawableRes = R.drawable.tile_e),
                    TileData(x = 2, y = 1, drawableRes = R.drawable.tile_f),
                    TileData(x = 3, y = 1, drawableRes = R.drawable.tile_g),
                    TileData(x = 4, y = 1, drawableRes = R.drawable.tile_h),
                    TileData(x = 1, y = 2, drawableRes = R.drawable.tile_i),
                    TileData(x = 2, y = 2, drawableRes = R.drawable.tile_j),
                    TileData(x = 4, y = 2, drawableRes = R.drawable.tile_k),
                    TileData(x = 0, y = 3, drawableRes = R.drawable.tile_x),
                    TileData(x = 1, y = 3, drawableRes = R.drawable.tile_m),
                    TileData(x = 2, y = 3, drawableRes = R.drawable.tile_n),
                    TileData(x = 3, y = 3, drawableRes = R.drawable.tile_o),
                    TileData(x = 0, y = 4, drawableRes = R.drawable.tile_p),
                    TileData(x = 2, y = 4, drawableRes = R.drawable.tile_q),
                    TileData(x = 3, y = 4, drawableRes = R.drawable.tile_r),
                    TileData(x = 4, y = 4, drawableRes = R.drawable.tile_s),
                    TileData(x = 1, y = 5, drawableRes = R.drawable.tile_t),
                    TileData(x = 2, y = 5, drawableRes = R.drawable.tile_a),
                    TileData(x = 4, y = 5, drawableRes = R.drawable.tile_b)
                )
            }

            PannableTileGrid(
                tiles = tiles,
                onTileClick = { x, y ->
                    println("Tapped tile at ($x, $y)")
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            val context = LocalContext.current // 👈 MOVE THIS OUTSIDE FIRST

            currentTile?.let { tile ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            Toast.makeText(context, "Tile ID: ${tile.id}", Toast.LENGTH_SHORT).show()
                        }
                        .border(2.dp, Color.Yellow, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                   // TileView(tile)
                    DrawTile(tile)


                    Text(
                        text = "ID: ${tile.id}",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { viewModel.requestTileFromBackend(gameId, "HOST") }) {
                    Text("Draw Tile")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { viewModel.rotateCurrentTile() }) {
                    Text("Rotate")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            BottomScreenBar()

        }
    }
}

data class TileData(
    val x: Int,
    val y: Int,
    val drawableRes: Int? = null
)

@Composable
fun PannableTileGrid(
    tiles: List<TileData>,
    onTileClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tileSize = 100.dp
    val tileSizePx = with(LocalDensity.current) { tileSize.toPx() }
    val context = LocalContext.current

    val scale = remember { mutableFloatStateOf(1f) }
    val offsetX = remember { mutableFloatStateOf(0f) }
    val offsetY = remember { mutableFloatStateOf(0f) }
    val imageCache = remember { mutableMapOf<Int, ImageBitmap>() }

    val gestureModifier = Modifier
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale.floatValue *= zoom
                offsetX.floatValue += pan.x
                offsetY.floatValue += pan.y
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                val tileSizePxScaled = tileSizePx * scale.floatValue
                val x = ((offset.x - offsetX.floatValue) / tileSizePxScaled).toInt()
                val y = ((offset.y - offsetY.floatValue) / tileSizePxScaled).toInt()
                onTileClick(x, y)
            }
        }

    Box(
        modifier = modifier
            .clipToBounds()
            .then(gestureModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaledTileSizePx = tileSizePx * scale.floatValue

            tiles.forEach { tile ->
                val x = tile.x
                val y = tile.y
                val left = x * scaledTileSizePx + offsetX.floatValue
                val top = y * scaledTileSizePx + offsetY.floatValue

                val image = tile.drawableRes?.let { resId ->
                    imageCache.getOrPut(resId) {
                        ContextCompat.getDrawable(context, resId)?.toBitmap()
                            ?.asImageBitmap() ?: ImageBitmap(1, 1)
                    }
                }

                if (image != null) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(scaledTileSizePx.toInt(), scaledTileSizePx.toInt())
                    )
                } else {
                    drawRect(
                        color = Color.Gray,
                        topLeft = Offset(left, top),
                        size = Size(scaledTileSizePx, scaledTileSizePx)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("Felix", "Sajo", "Jakob", "Mike", "Almin").forEachIndexed { index, name ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Player",
                    tint = if (index == 0) Color.Green else Color.White,
                    modifier = Modifier.size(30.dp)
                )
                Text(name, fontSize = 12.sp,
                    color = if (index == 0) Color.Green else Color.White)
            }
        }
    }
}

@Composable
fun BottomScreenBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp) // Höhe so bemessen, dass Badge und Karte Platz haben
    ) {
        // Meeple-Icon links, Spacer, Karte+Badge rechts
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Linke Seite: Meeple + Count unterhalb
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.meeple_blu),
                        contentDescription = "Meeple",
                        modifier = Modifier.size(65.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "8x",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Platzhalter für gezogene Karte
            Box(
                modifier = Modifier.height(170.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Image(
                    painter = painterResource(id = R.drawable.tile_j),
                    contentDescription = "Demo Tile",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(135.dp)
                )
            }

            // Rechte Seite: Karte + Punkt-Badge
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Karten-Rückseite
                    Image(
                        painter = painterResource(id = R.drawable.tile_back),
                        contentDescription = "Tile Back",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.size(65.dp)
                    )

                    Spacer(modifier = Modifier.height(13.dp))

                    // Punkte-Badge unten an der Karte
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 25.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "10P",
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
fun TileBackRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(4) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TileBackButton(
                    label = "18",
                    onClick = {}
                )
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

@Composable
fun TileBackButton(
    label: String,
    onClick: () -> Unit,
    backgroundRes: Int = R.drawable.tile_back
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clickable { onClick() }
    ) {
        Image(
            painter = painterResource(id = backgroundRes),
            contentDescription = "Tile back image",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )

        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
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
fun DrawTile(tile: Tile) {
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
                .size(64.dp)
                .graphicsLayer(
                    rotationZ = when (tile.tileRotation) {
                        TileRotation.NORTH -> 0f
                        TileRotation.EAST -> 90f
                        TileRotation.SOUTH -> 180f
                        TileRotation.WEST -> 270f
                    }
                )
        )
    } else {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.Red),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "?", color = Color.White)
        }
    }
}
