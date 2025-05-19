package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.se2_ss2025_gruppec.carcasonnefrontend.ApiClient
import at.se2_ss2025_gruppec.carcasonnefrontend.ApiClient.authApi
import at.se2_ss2025_gruppec.carcasonnefrontend.LoginRequest
import at.se2_ss2025_gruppec.carcasonnefrontend.RegisterRequest
import at.se2_ss2025_gruppec.carcasonnefrontend.TokenManager
import at.se2_ss2025_gruppec.carcasonnefrontend.parseErrorMessage
import kotlinx.coroutines.launch
import retrofit2.HttpException

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)

    @SuppressLint("StaticFieldLeak")
    private val context = getApplication<Application>().applicationContext

    fun login(onAuthSuccess: (String) -> Unit) {
        isLoading = true

        viewModelScope.launch {
            val request = LoginRequest(username, password)
            try {
                val response = ApiClient.authApi.login(request) //Store TokenResponse in val response
                TokenManager.userToken = response.token
                onAuthSuccess(response.token) //Pass actual JWT string to onAuthSuccess
            } catch (e: HttpException) { //Catch HTTP-specific exceptions
                val errorBody = e.response()?.errorBody()?.string() //Get raw error body from HTTP response
                val errorMsg = parseErrorMessage(errorBody) //Parse actual message from error body JSON
                Toast.makeText(context, "Login failed: $errorMsg", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { //Catch-all for other exceptions
                Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                authApi.register(request) //Success message easier to handle here, no need to store HTTP 201 from backend
                Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                val errorMsg = parseErrorMessage(errorBody)
                Toast.makeText(context, "Registration failed: $errorMsg", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                isLoading = false
                Toast.makeText(context, "Registration failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

}