package at.se2_ss2025_gruppec.carcasonnefrontend

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
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
                composable("main") { MainScreen(navController) }
                composable("join_game") { JoinGameScreen(navController) }
                composable("create_game") { CreateGameScreen(navController) }

                composable("lobby/{gameId}/{playerCount}/{playerName}") { backStackEntry ->
                    val gameId = backStackEntry.arguments?.getString("gameId") ?: ""
                    val playerCount = backStackEntry.arguments?.getString("playerCount")?.toIntOrNull() ?: 2
                    val playerName = backStackEntry.arguments?.getString("playerName") ?: ""

                    stompClient?.let { //Lobby only accessible when stompClient not null
                        LobbyScreen(
                            gameId = gameId,
                            playerName = playerName,
                            playerCount = playerCount,
                            stompClient = it,
                            navController = navController
                        )
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
        Image(
            painter = painterResource(id = R.drawable.bg_pxart),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

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
        Image(
            painter = painterResource(id = R.drawable.bg_pxart),
            contentDescription = null,
            contentScale = ContentScale.Crop, //Maintains aspect ratio (FillBounds causes warping!)
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 240.dp),
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
                Spacer(modifier = Modifier.height(24.dp))

                StyledGameButton( //Login button
                    label = if (isLoading) "Logging in..." else "Login", //Simple loading indicator
                    onClick = {
                        if (!isLoading) {
                            login(username, password)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                StyledGameButton( //Register button
                    label = if (isLoading) "Registering..." else "Register",
                    onClick = {
                        if (!isLoading) {
                            register(username, password)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                StyledGameButton( //Just for development
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
        Image(
            painter = painterResource(id = R.drawable.bg_pxart),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 240.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StyledGameButton(
                    label = "Join Game",
                    onClick = {
                        navController.navigate("join_game")
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                StyledGameButton(
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
        Image(
            painter = painterResource(id = R.drawable.bg_pxart),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
        )

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

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (gameId.isBlank() || playerName.isBlank()) {
                            Toast.makeText(context, "Enter both game ID and player name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        coroutineScope.launch {
                            try {
                                //Retrieve token from Singleton and append it to API call
                                val token = TokenManager.userToken ?: throw IllegalStateException("Token is required, but was null!")
                                val gameState = gameApi.getGame(
                                    token = "Bearer $token",
                                    gameId = gameId)
                                val playerCount = gameState.players.size.coerceAtLeast(2)
                                navController.navigate("lobby/$gameId/$playerCount/$playerName")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Game not found or connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .width(130.dp)
                        .height(45.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xCC5A3A1A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Join",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        color = Color(0xFFFFF4C2)
                    )
                }
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
        Image(
            painter = painterResource(id = R.drawable.bg_pxart),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
        )

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

                Button(
                    onClick = {
                        val hostName = playerName.trim()
                        if (hostName.isEmpty()) {
                            Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                            return@Button
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
                    },
                    modifier = Modifier
                        .width(240.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xCC5A3A1A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Create Game",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
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
        stompClient.sendJoinGame(gameId, playerName)

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
        Image(
            painter = painterResource(id = R.drawable.bg_pxart),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

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

data class Tile(
    val top: Color,
    val right: Color,
    val bottom: Color,
    val left: Color
) {
    fun rotated(): Tile {
        return Tile(
            top = left,
            right = top,
            bottom = right,
            left = bottom
        )
    }
}

fun generateRandomTile(): Tile {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)
    return Tile(
        top = colors.random(),
        right = colors.random(),
        bottom = colors.random(),
        left = colors.random()
    )
}

fun canPlaceTile(
    grid: List<List<Tile?>>, x: Int, y: Int, tile: Tile
): Boolean {
    val top = if (y > 0) grid[y - 1][x] else null
    val bottom = if (y < grid.size - 1) grid[y + 1][x] else null
    val left = if (x > 0) grid[y][x - 1] else null
    val right = if (x < grid[y].size - 1) grid[y][x + 1] else null

    if (top == null && bottom == null && left == null && right == null) return false

    return (top == null || top.bottom == tile.top) &&
            (bottom == null || bottom.top == tile.bottom) &&
            (left == null || left.right == tile.left) &&
            (right == null || right.left == tile.right)
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

                drawRect(color = tile.top, topLeft = Offset(0f, 0f), size = Size(width, edge))
                drawRect(
                    color = tile.right,
                    topLeft = Offset(width - edge, 0f),
                    size = Size(edge, height)
                )
                drawRect(
                    color = tile.bottom,
                    topLeft = Offset(0f, height - edge),
                    size = Size(width, edge)
                )
                drawRect(color = tile.left, topLeft = Offset(0f, 0f), size = Size(edge, height))
                drawCircle(Color.Black, radius = 4f, center = Offset(width / 2, height / 2))
            }
    )
}

@Composable
fun GameplayScreen(gameId: String) {
    val gridSize = 5
    val grid = remember {
        mutableStateListOf<MutableList<Tile?>>().apply {
            repeat(gridSize) {
                add(MutableList(gridSize) { null })
            }
            this[gridSize / 2][gridSize / 2] = generateRandomTile() // center tile
        }
    }
    val context = LocalContext.current
    val currentTile = remember { mutableStateOf(generateRandomTile()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Game ID: $gameId",
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        //Grid
        Column {
            for (y in 0 until gridSize) {
                Row {
                    for (x in 0 until gridSize) {
                        val tile = grid[y][x]
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .padding(2.dp)
                                .background(Color.DarkGray)
                                .clickable {
                                    if (tile == null && canPlaceTile(
                                            grid,
                                            x,
                                            y,
                                            currentTile.value
                                        )
                                    ) {
                                        grid[y][x] = currentTile.value
                                        currentTile.value = generateRandomTile()
                                    } else {
                                        Toast
                                            .makeText(
                                                context,
                                                "Can't place tile here",
                                                Toast.LENGTH_SHORT
                                            )
                                            .show()
                                    }
                                }
                        ) {
                            tile?.let {
                                TileView(it)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Next Tile:", color = Color.White)
        TileView(currentTile.value)
        Button(
            onClick = {
                currentTile.value = generateRandomTile()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Gray,
                contentColor = Color.White
            ),
            modifier = Modifier
                .padding(top = 12.dp)
        ) {
            Text("Skip Tile")
        }
        Spacer(modifier = Modifier.width(16.dp))

        Button(
            onClick = {
                currentTile.value = currentTile.value.rotated()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Gray,
                contentColor = Color.White
            ),
            modifier = Modifier
                .padding(top = 12.dp)
        ) {
            Text("Rotate Tile")
        }
    }
}

@Composable
fun StyledGameButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(240.dp)
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xCC5A3A1A)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp,
            focusedElevation = 8.dp
        ),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = Color(0xFFFFF4C2)
            )
        }
    }
}