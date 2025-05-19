package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.se2_ss2025_gruppec.carcasonnefrontend.ApiClient.authApi
import at.se2_ss2025_gruppec.carcasonnefrontend.LoginRequest
import at.se2_ss2025_gruppec.carcasonnefrontend.RegisterRequest
import at.se2_ss2025_gruppec.carcasonnefrontend.TokenManager
import at.se2_ss2025_gruppec.carcasonnefrontend.parseErrorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class AuthViewModel: ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)

    private val _uiEvents = MutableSharedFlow<String>() // Let only ViewModel emit messages into flow
    val uiEvents = _uiEvents.asSharedFlow() // Expose flow read-only to AuthScreen

    fun login(onAuthSuccess: (String) -> Unit) {
        isLoading = true

        viewModelScope.launch {
            val request = LoginRequest(username, password)
            try {
                val response = authApi.login(request) // Store TokenResponse in val response
                TokenManager.userToken = response.token
                onAuthSuccess(response.token) // Pass actual JWT string to onAuthSuccess
            } catch (e: HttpException) { // Catch HTTP-specific exceptions
                val errorBody = e.response()?.errorBody()?.string() // Get raw error body from HTTP response
                val errorMsg = parseErrorMessage(errorBody) // Parse actual message from error body JSON
                _uiEvents.emit("Login failed: $errorMsg")
            } catch (e: Exception) { // Catch-all for other exceptions
                _uiEvents.emit("Login failed: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun register() {
        isLoading = true

        viewModelScope.launch {
            val request = RegisterRequest(username, password)
            try {
                authApi.register(request) // Success message easier to handle here, no need to store HTTP 201 from backend
                _uiEvents.emit("Registration successful!")
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                val errorMsg = parseErrorMessage(errorBody)
                _uiEvents.emit("Registration failed: $errorMsg")
            } catch (e: Exception) {
                _uiEvents.emit("Registration failed: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
}