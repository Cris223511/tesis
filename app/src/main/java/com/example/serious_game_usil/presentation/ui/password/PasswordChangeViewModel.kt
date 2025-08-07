package com.example.serious_game_usil.presentation.ui.password

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.launch

class PasswordChangeViewModel(private val repository: IUserRepository) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _validationResult = MutableLiveData<Boolean?>()
    val validationResult: LiveData<Boolean?> = _validationResult

    private val _otpSent = MutableLiveData<Boolean>()
    val otpSent: LiveData<Boolean> = _otpSent

    private val _otpVerified = MutableLiveData<Boolean>()
    val otpVerified: LiveData<Boolean> = _otpVerified

    private val _passwordChanged = MutableLiveData<Boolean>()
    val passwordChanged: LiveData<Boolean> = _passwordChanged
    
    private val _otpCode = MutableLiveData<String>()
    val otpCode: LiveData<String> = _otpCode

    fun validateEmail(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            when (val result = repository.validateEmailForPasswordChange(email)) {
                is ApiResult.Success -> {
                    Log.d("PasswordChangeVM", "Email válido")
                    _validationResult.value = true
                    _error.value = null
                }
                is ApiResult.Error -> {
                    Log.e("PasswordChangeVM", "Error al validar email: ${result.message}")
                    _error.value = result.message
                    _validationResult.value = null
                }
                is ApiResult.NetworkError -> {
                    Log.e("PasswordChangeVM", "Error de red al validar email", result.exception)
                    _error.value = "Error de conexión: ${result.exception.message}"
                    _validationResult.value = null
                }
            }

            _isLoading.value = false
        }
    }

    fun sendPasswordOTP(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            when (val result = repository.sendPasswordChangeOTP(email)) {
                is ApiResult.Success -> {
                    Log.d("PasswordChangeVM", "OTP generado: ${result.data.otpCode}")
                    _otpCode.value = result.data.otpCode
                    _otpSent.value = true
                    _error.value = null
                }
                is ApiResult.Error -> {
                    Log.e("PasswordChangeVM", "Error al enviar OTP: ${result.message}")
                    _error.value = result.message
                }
                is ApiResult.NetworkError -> {
                    Log.e("PasswordChangeVM", "Error de red al enviar OTP", result.exception)
                    _error.value = "Error de conexión: ${result.exception.message}"
                }
            }

            _isLoading.value = false
        }
    }

    fun verifyPasswordOTP(email: String, otpCode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            when (val result = repository.verifyPasswordOTP(email, otpCode)) {
                is ApiResult.Success -> {
                    Log.d("PasswordChangeVM", "OTP verificado exitosamente")
                    _otpVerified.value = true
                    _error.value = null
                }
                is ApiResult.Error -> {
                    Log.e("PasswordChangeVM", "Error al verificar OTP: ${result.message}")
                    _error.value = result.message
                    _otpVerified.value = false
                }
                is ApiResult.NetworkError -> {
                    Log.e("PasswordChangeVM", "Error de red al verificar OTP", result.exception)
                    _error.value = "Error de conexión: ${result.exception.message}"
                    _otpVerified.value = false
                }
            }

            _isLoading.value = false
        }
    }

    fun changePasswordWithOTP(email: String, otpCode: String, newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            when (val result = repository.changePasswordWithOTP(email, otpCode, newPassword, confirmPassword)) {
                is ApiResult.Success -> {
                    Log.d("PasswordChangeVM", "Contraseña cambiada exitosamente")
                    _passwordChanged.value = true
                    _error.value = null
                }
                is ApiResult.Error -> {
                    Log.e("PasswordChangeVM", "Error al cambiar contraseña: ${result.message}")
                    _error.value = result.message
                    _passwordChanged.value = false
                }
                is ApiResult.NetworkError -> {
                    Log.e("PasswordChangeVM", "Error de red al cambiar contraseña", result.exception)
                    _error.value = "Error de conexión: ${result.exception.message}"
                    _passwordChanged.value = false
                }
            }

            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearOTPSent() {
        _otpSent.value = false
    }
}