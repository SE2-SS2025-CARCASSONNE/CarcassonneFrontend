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
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import at.se2_ss2025_gruppec.carcasonnefrontend.ApiClient.authApi
import at.se2_ss2025_gruppec.carcasonnefrontend.ApiClient.gameApi
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.Callbacks
import kotlinx.coroutines.launch
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import kotlinx.coroutines.delay
import org.json.JSONObject
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()
            var userToken by remember { mutableStateOf(TokenManager.userToken) }

            val stompClient by remember(userToken) { //Remember stompClient across recompositions, unless userToken changes
                mutableStateOf(TokenManager.userToken?.let { //Init of stompClient only after authentication (= when userToken not null)
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
                    TokenManager.userToken = jwtToken //Store JWT in Singleton for persistence and easy access
                    userToken = jwtToken //Triggers recomposition to init stompClient
                    navController.navigate("main") {
                        popUpTo("auth") { inclusive = true } //Lock user out of auth screen after authentication
                    }
                })}
                composable("main") { GameplayScreen("1") } // Just for dev, to avoid having to run backend, change back later
                composable("join_game") { JoinGameScreen(navController) }
                composable("create_game") { CreateGameScreen(navController) }

                composable("lobby/{gameId}/{playerCount}/{playerName}") { backStackEntry ->
                    val gameId = backStackEntry.arguments?.getString("gameId") ?: ""
                    val playerCount = backStackEntry.arguments?.getString("playerCount")?.toIntOrNull() ?: 2
                    val playerName = backStackEntry.arguments?.getString("playerName") ?: ""

                    stompClient?.let {
                        LobbyScreen(
                            gameId = gameId,
                            playerName = playerName,
                            playerCount = playerCount,
                            stompClient = it,
                            navController = navController
                        )
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
fun AuthScreen(onAuthSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { (mutableStateOf(false)) }

    fun login(username: String, password: String) {
        isLoading = true

        coroutineScope.launch {
            val request = LoginRequest(username, password)
            try {
                val response = authApi.login(request) //Store TokenResponse in val response
                TokenManager.userToken = response.token
                onAuthSuccess(response.token) //Pass actual JWT string to onAuthSuccess
            } catch (e: retrofit2.HttpException) { //Catch HTTP-specific exceptions
                val errorBody = e.response()?.errorBody()?.string() //Get raw error body from HTTP response
                val errorMsg = parseErrorMessage(errorBody) //Parse actual message from error body JSON
                isLoading = false
                Toast.makeText(context, "Login failed: $errorMsg", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { //Catch-all for other exceptions
                isLoading = false
                Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    fun register(username: String, password: String) {
        isLoading = true

        coroutineScope.launch {
            val request = RegisterRequest(username, password)
            try {
                authApi.register(request) //Success message easier to handle here, no need to store HTTP 201 from backend
                isLoading = false
                Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                val errorMsg = parseErrorMessage(errorBody)
                isLoading = false
                Toast.makeText(context, "Registration failed: $errorMsg", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                isLoading = false
                Toast.makeText(context, "Registration failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                    onValueChange = { username = it },
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
                    onValueChange = { password = it },
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
                            login(username, password)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                PixelArtButton( //Register button
                    label = if (isLoading) "Registering..." else "Register",
                    onClick = {
                        if (!isLoading) {
                            register(username, password)
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
                .padding(bottom = 240.dp),
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
    var playerName by remember { mutableStateOf("") }
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

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Enter your name") },
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
                        if (gameId.isBlank() || playerName.isBlank()) {
                            Toast.makeText(context, "Enter game ID and player name", Toast.LENGTH_SHORT).show()
                            return@PixelArtButton
                        }

                        coroutineScope.launch {
                            try {
                                val token = TokenManager.userToken
                                    ?: throw IllegalStateException("Token is required, but was null!")

                                val gameState = gameApi.getGame(
                                    token = "Bearer $token", // Append JWT to API call
                                    gameId = gameId
                                )
                                val playerCount = gameState.players.size.coerceAtLeast(2)

                                navController.navigate("lobby/$gameId/$playerCount/$playerName")
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
    var playerName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundImage()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 210.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))

                //Name input field
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Enter your name") },
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

                Spacer(modifier = Modifier.height(24.dp))

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
                        val hostName = playerName.trim()
                        if (hostName.isEmpty()) {
                            Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                            return@PixelArtButton
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
                                navController.navigate("lobby/${response.gameId}/$selectedPlayers/$hostName")

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
fun LobbyScreen(gameId: String, playerName: String, playerCount: Int = 2, stompClient: MyClient, navController: NavController) {
    val players = remember { mutableStateListOf(playerName) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (!stompClient.isConnected()) {
            delay(100)
        }
        try {
            Log.d("LobbyScreen", "Sending join_game for $playerName to $gameId")
            stompClient.sendJoinGame(gameId, playerName)
        } catch (e: Exception) {
            Log.e("LobbyScreen", "Failed to send join_game: ${e.message}")
            Toast.makeText(context, "Failed to join game: ${e.message}", Toast.LENGTH_LONG).show()
        }
        stompClient.listenOn("/topic/game/$gameId") { message ->
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
                    Handler(Looper.getMainLooper()).post {
                        players.clear()
                        for (i in 0 until playerArray.length()) {
                            players.add(playerArray.getString(i))
                        }
                        Log.d("Lobby", "Updated players in lobby: $players")
                    }
                }
            }
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

            for (i in 1..playerCount) {
                val playerNameInList = players.getOrNull(i - 1) ?: "Empty Slot"
                Log.d("LobbyScreen", "Checking: [$playerNameInList] vs [$playerName]")
                val displayName = if (playerNameInList.trim() == playerName.trim()) "You" else playerNameInList

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

@Preview(showBackground = true)
@Composable
fun GameplayScreenPreview() {
    GameplayScreen("123")
}

@Composable
fun GameplayScreen(gameId: String) {
    Box(modifier = Modifier.fillMaxSize()) {
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

            Spacer(modifier = Modifier.height(130.dp))

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
    val tileSize = 120.dp
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
            .height(72.dp)
            .background(Color.White)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.button_pxart),
                    contentDescription = "Meeple",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                Text("8x", color = Color.Black, fontSize = 16.sp)
            }

            Box(
                modifier = Modifier
                    .size(98.dp)
                    .background(Color.Green)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Gray)
                )
                Text("10P", color = Color.Black, modifier = Modifier.padding(start = 4.dp))
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