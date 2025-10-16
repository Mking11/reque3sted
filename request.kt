Kotlin
// --- src/main/java/com/yourpackage/data/UserRepository.kt ---

import kotlinx.coroutines.delay

data class User(
    val id: Int,
    val name: String? = null,
    val age: Int? = null,
    val gender: String? = null
)

interface UserRepository {
    suspend fun insertUser(user: User)
    suspend fun updateUser(user: User)
    suspend fun deleteUser(user: User)
    suspend fun getUserById(id: Int): User?
}

// Corrected Implementation: No custom CoroutineScope needed.
class UserRepositoryImpl : UserRepository {

    // Simulating a remote or local data source
    private val userCache = mutableMapOf(
        1 to User(id = 1, name = "Michael Mekonnen", age = 29, gender = "Male")
    )

    override suspend fun insertUser(user: User) {
        delay(500) // Simulate network/db latency
        println("Inserting user: $user")
        userCache[user.id] = user
    }

    override suspend fun updateUser(user: User) {
        delay(500) // Simulate network/db latency
        println("Updating user: $user")
        if (userCache.containsKey(user.id)) {
            userCache[user.id] = user
        }
    }

    override suspend fun deleteUser(user: User) {
        delay(500) // Simulate network/db latency
        println("Deleting user: $user")
        userCache.remove(user.id)
    }

    override suspend fun getUserById(id: Int): User? {
        delay(1000) // Simulate network/db latency
        println("Fetching user by id: $id")
        return userCache[id]
    }
}

// --- src/main/java/com/yourpackage/di/AppModule.kt ---

import com.yourpackage.data.UserRepository
import com.yourpackage.data.UserRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // The dependency will live as long as the application
object AppModule {

    @Provides
    @Singleton // Ensures only one instance of the repository is created
    fun provideUserRepository(): UserRepository {
        // Here we specify which implementation to use for the UserRepository interface
        return UserRepositoryImpl()
    }
}
// --- src/main/java/com/yourpackage/profile/ProfileContract.kt ---

import com.yourpackage.data.User

// Represents the state of the UI at any given time
data class ProfileUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val isSaved: Boolean = false
)

// Represents actions the user can perform on the UI
sealed interface ProfileEvent {
    data class LoadUser(val userId: Int) : ProfileEvent
    data class UpdateUserName(val name: String) : ProfileEvent
    object SaveUser : ProfileEvent
}

// --- src/main/java/com/yourpackage/profile/ProfileViewModel.kt ---

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourpackage.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository // Hilt provides this from AppModule
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // Load initial data when the ViewModel is created
        onEvent(ProfileEvent.LoadUser(1))
    }

    // Single entry point for all UI actions
    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.LoadUser -> loadUser(event.userId)
            is ProfileEvent.UpdateUserName -> updateUserName(event.name)
            is ProfileEvent.SaveUser -> saveUser()
        }
    }

    private fun loadUser(userId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = userRepository.getUserById(userId)
                _uiState.update { it.copy(isLoading = false, user = user) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load user") }
            }
        }
    }

    private fun updateUserName(name: String) {
        _uiState.update { currentState ->
            currentState.copy(
                user = currentState.user?.copy(name = name),
                isSaved = false // Reset save status on edit
            )
        }
    }

    private fun saveUser() {
        val currentUser = _uiState.value.user ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                userRepository.updateUser(currentUser)
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to save user") }
            }
        }
    }
}

// --- src/main/java/com/yourpackage/profile/ProfileScreen.kt ---

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show a snackbar when saving is successful
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("User profile saved!")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.error != null -> Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                uiState.user != null -> ProfileContent(
                    user = uiState.user!!,
                    onEvent = viewModel::onEvent // Pass the event handler directly
                )
            }
        }
    }
}

@Composable
fun ProfileContent(
    user: com.yourpackage.data.User,
    onEvent: (ProfileEvent) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        OutlinedTextField(
            value = user.name ?: "",
            onValueChange = { newName ->
                onEvent(ProfileEvent.UpdateUserName(newName))
            },
            label = { Text("Name") }
        )
        Text(text = "Age: ${user.age ?: "N/A"}")
        Text(text = "Gender: ${user.gender ?: "N/A"}")
        
        Button(onClick = { onEvent(ProfileEvent.SaveUser) }) {
            Text("Save Changes")
        }
    }
}

