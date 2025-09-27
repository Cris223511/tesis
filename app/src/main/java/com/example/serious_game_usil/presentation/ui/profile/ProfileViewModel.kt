package com.example.serious_game_usil.presentation.ui.administrador.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.UserProfileResponse
import com.example.serious_game_usil.data.ChildrenResponse
import com.example.serious_game_usil.`interface`.PhotoChangesResponse
import com.example.serious_game_usil.`interface`.UploadBannerResponse
import com.example.serious_game_usil.`interface`.UploadPhotoResponse
import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: IUserRepository) : ViewModel() {

    private val _profileData = MutableLiveData<UserProfileResponse?>()
    val profileData: LiveData<UserProfileResponse?> = _profileData

    private val _childrenData = MutableLiveData<ChildrenResponse?>()
    val childrenData: LiveData<ChildrenResponse?> = _childrenData

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _photoUploadResult = MutableLiveData<UploadPhotoResponse?>()
    val photoUploadResult: LiveData<UploadPhotoResponse?> = _photoUploadResult
    
    private val _photoChanges = MutableLiveData<PhotoChangesResponse?>()
    val photoChanges: LiveData<PhotoChangesResponse?> = _photoChanges
    
    private val _isUploadingPhoto = MutableLiveData<Boolean>()
    val isUploadingPhoto: LiveData<Boolean> = _isUploadingPhoto
    
    private val _bannerUploadResult = MutableLiveData<UploadBannerResponse?>()
    val bannerUploadResult: LiveData<UploadBannerResponse?> = _bannerUploadResult
    
    private val _bannerChanges = MutableLiveData<com.example.serious_game_usil.`interface`.BannerChangesResponse?>()
    val bannerChanges: LiveData<com.example.serious_game_usil.`interface`.BannerChangesResponse?> = _bannerChanges
    
    private val _isUploadingBanner = MutableLiveData<Boolean>()
    val isUploadingBanner: LiveData<Boolean> = _isUploadingBanner
    
    private val _profileUpdateResult = MutableLiveData<UserProfileResponse?>()
    val profileUpdateResult: LiveData<UserProfileResponse?> = _profileUpdateResult
    
    private val _profileChanges = MutableLiveData<com.example.serious_game_usil.`interface`.ProfileChangesResponse?>()
    val profileChanges: LiveData<com.example.serious_game_usil.`interface`.ProfileChangesResponse?> = _profileChanges

    fun loadCurrentUserProfile() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "Iniciando carga de perfil actual")
            _isLoading.value = true

            when (val result = repository.getCurrentUserProfile()) {
                is ApiResult.Success -> {
                    Log.d("ProfileViewModel", "Perfil cargado exitosamente: ${result.data}")
                    Log.d("ProfileViewModel", "Foto presente: ${!result.data.fotoMovil.isNullOrEmpty()}")
                    result.data.fotoMovil?.let {
                        Log.d("ProfileViewModel", "Tamaño de foto: ${it.length} caracteres")
                    }
                    _profileData.value = result.data
                    _error.value = null
                }
                is ApiResult.Error -> {
                    Log.e("ProfileViewModel", "Error al cargar perfil: ${result.message}")
                    _error.value = result.message
                }
                is ApiResult.NetworkError -> {
                    Log.e("ProfileViewModel", "Error de red: ${result.exception.message}")
                    _error.value = "Error de conexión: ${result.exception.message}"
                }
            }

            _isLoading.value = false
        }
    }

    fun loadUserProfile(userId: Int) {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "Iniciando carga de perfil para usuario: $userId")
            _isLoading.value = true

            when (val result = repository.getUserProfile(userId)) {
                is ApiResult.Success -> {
                    Log.d("ProfileViewModel", "Perfil del usuario $userId cargado exitosamente: ${result.data}")
                    _profileData.value = result.data
                    _error.value = null
                }
                is ApiResult.Error -> {
                    Log.e("ProfileViewModel", "Error al cargar perfil del usuario $userId: ${result.message}, código: ${result.code}")
                    _error.value = when(result.code) {
                        403 -> "No tienes permisos para ver este perfil"
                        404 -> "Usuario no encontrado"
                        else -> result.message
                    }
                }
                is ApiResult.NetworkError -> {
                    Log.e("ProfileViewModel", "Error de red al cargar perfil del usuario $userId: ${result.exception.message}")
                    _error.value = "Error de conexión: ${result.exception.message}"
                }
            }

            _isLoading.value = false
        }
    }

    fun loadUserChildren() {
        viewModelScope.launch {
            when (val result = repository.getUserChildren()) {
                is ApiResult.Success -> {
                    _childrenData.value = result.data
                    _error.value = null
                }
                is ApiResult.Error -> {
                    _error.value = result.message
                }
                is ApiResult.NetworkError -> {
                    _error.value = "Error de conexión: ${result.exception.message}"
                }
            }
        }
    }

    fun uploadPhoto(base64Photo: String) {
        viewModelScope.launch {
            _isUploadingPhoto.value = true
            _error.value = null
            
            when (val result = repository.uploadUserPhoto(base64Photo)) {
                is ApiResult.Success -> {
                    Log.d("ProfileViewModel", "Foto subida exitosamente")
                    _photoUploadResult.value = result.data
                    _error.value = null
                }
                is ApiResult.Error -> {
                    Log.e("ProfileViewModel", "Error al subir foto: ${result.code} - ${result.message}")
                    _error.value = when(result.code) {
                        403 -> "Has alcanzado el límite máximo de 2 cambios de foto"
                        413 -> "La imagen excede el tamaño máximo de 5MB"
                        400 -> "Formato de imagen inválido"
                        else -> result.message ?: "Error al subir la foto"
                    }
                    _photoUploadResult.value = null
                }
                is ApiResult.NetworkError -> {
                    Log.e("ProfileViewModel", "Error de red al subir foto", result.exception)
                    _error.value = "Error de conexión: ${result.exception.message}"
                    _photoUploadResult.value = null
                }
            }
            
            _isUploadingPhoto.value = false
        }
    }
    
    fun checkPhotoChanges() {
        viewModelScope.launch {
            when (val result = repository.getPhotoChanges()) {
                is ApiResult.Success -> {
                    _photoChanges.value = result.data
                    _error.value = null
                }
                is ApiResult.Error -> {
                    _error.value = result.message
                }
                is ApiResult.NetworkError -> {
                    _error.value = "Error de conexión: ${result.exception.message}"
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
    
    fun uploadBanner(base64Banner: String) {
        viewModelScope.launch {
            _isUploadingBanner.value = true
            _error.value = null
            
            when (val result = repository.uploadUserBanner(base64Banner)) {
                is ApiResult.Success -> {
                    Log.d("ProfileViewModel", "Banner subido exitosamente")
                    _bannerUploadResult.value = result.data
                    _error.value = null
                }
                is ApiResult.Error -> {
                    Log.e("ProfileViewModel", "Error al subir banner: ${result.code} - ${result.message}")
                    _error.value = when(result.code) {
                        403 -> "Has alcanzado el límite máximo de 2 cambios de banner"
                        413 -> "La imagen excede el tamaño máximo de 5MB"
                        400 -> "Formato de imagen inválido"
                        else -> result.message ?: "Error al subir el banner"
                    }
                    _bannerUploadResult.value = null
                }
                is ApiResult.NetworkError -> {
                    Log.e("ProfileViewModel", "Error de red al subir banner", result.exception)
                    _error.value = "Error de conexión: ${result.exception.message}"
                    _bannerUploadResult.value = null
                }
            }
            
            _isUploadingBanner.value = false
        }
    }
    
    fun checkBannerChanges() {
        viewModelScope.launch {
            when (val result = repository.getBannerChanges()) {
                is ApiResult.Success -> {
                    _bannerChanges.value = result.data
                    _error.value = null
                }
                is ApiResult.Error -> {
                    _error.value = result.message
                }
                is ApiResult.NetworkError -> {
                    _error.value = "Error de conexión: ${result.exception.message}"
                }
            }
        }
    }

    fun clearPhotoUploadResult() {
        _photoUploadResult.value = null
    }
    
    fun clearBannerUploadResult() {
        _bannerUploadResult.value = null
    }
    
    fun updateProfile(correo: String, telefono: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            when (val result = repository.updateProfile(correo, telefono)) {
                is ApiResult.Success -> {
                    Log.d("ProfileViewModel", "Perfil actualizado exitosamente")
                    _profileUpdateResult.value = result.data
                    _profileData.value = result.data
                    _error.value = null
                }
                is ApiResult.Error -> {
                    Log.e("ProfileViewModel", "Error al actualizar perfil: ${result.code} - ${result.message}")
                    _error.value = when(result.code) {
                        400 -> "Datos inválidos. Verifique la información ingresada"
                        403 -> "No tienes permisos para realizar esta operación"
                        else -> result.message ?: "Error al actualizar el perfil"
                    }
                    _profileUpdateResult.value = null
                }
                is ApiResult.NetworkError -> {
                    Log.e("ProfileViewModel", "Error de red al actualizar perfil", result.exception)
                    _error.value = "Error de conexión: ${result.exception.message}"
                    _profileUpdateResult.value = null
                }
            }
            
            _isLoading.value = false
        }
    }
    
    fun clearProfileUpdateResult() {
        _profileUpdateResult.value = null
    }
    
    fun checkProfileChanges() {
        viewModelScope.launch {
            when (val result = repository.getProfileChanges()) {
                is ApiResult.Success -> {
                    _profileChanges.value = result.data
                    _error.value = null
                }
                is ApiResult.Error -> {
                    _error.value = result.message
                }
                is ApiResult.NetworkError -> {
                    _error.value = "Error de conexión: ${result.exception.message}"
                }
            }
        }
    }
}