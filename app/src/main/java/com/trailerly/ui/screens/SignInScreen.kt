package com.trailerly.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.trailerly.R
import com.trailerly.viewmodel.AuthViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import com.trailerly.auth.AuthResult
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.CommonStatusCodes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import com.trailerly.auth.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit = {},
    onLoginSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Google Sign-In configuration - Create fresh instance each time
    val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    
    val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    // State for Google Sign-In in progress
    val isGoogleSignInInProgress = remember { mutableStateOf(false) }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isGoogleSignInInProgress.value = false // Fixed
        if (result.resultCode != Activity.RESULT_OK) {
            Timber.d("Google Sign-In cancelled or failed")
            FirebaseCrashlytics.getInstance().log("Google Sign-In cancelled or failed")
            scope.launch {
                viewModel.setAuthResult(AuthResult.Error("Google Sign-In was cancelled or failed. Please try again."))
            }
            return@rememberLauncherForActivityResult
        }
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                scope.launch {
                    viewModel.signInWithGoogle(idToken)
                }
            } else {
                Timber.e("Google Sign-In failed: ID token is null")
                FirebaseCrashlytics.getInstance().log("Google Sign-In failed: ID token is null")
                // Set error state
                scope.launch {
                    viewModel.setAuthResult(AuthResult.Error("Failed to get Google ID token"))
                }
            }
        } catch (e: ApiException) {
            Timber.e("Google Sign-In failed: ${e.statusCode} - ${e.message}")
            FirebaseCrashlytics.getInstance().log("Google Sign-In failed: ${e.statusCode} - ${e.message}")
            val errorMessage = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Google Sign-In was cancelled"
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Google Sign-In failed"
                CommonStatusCodes.NETWORK_ERROR -> "Network error during Google Sign-In"
                else -> "Google Sign-In error: ${e.message}"
            }
            scope.launch {
                viewModel.setAuthResult(AuthResult.Error(errorMessage))
            }
        }
    }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    val authResult by viewModel.authResult.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    val isLoading = authResult is AuthResult.Loading
    val isFormValid = email.isNotBlank() && password.isNotBlank() &&
                     (!isSignUp || confirmPassword.isNotBlank())

    // Handle auth result
    LaunchedEffect(authResult) {
        when (authResult) {
            is AuthResult.Success -> {
                FirebaseCrashlytics.getInstance().setCustomKey("auth_success", true)
                Timber.d("Authentication successful")
                onLoginSuccess()
                onNavigateToHome()
            }
            is AuthResult.Error -> {
                FirebaseCrashlytics.getInstance().setCustomKey("auth_error", (authResult as AuthResult.Error).message)
                Timber.e("Authentication error: ${(authResult as AuthResult.Error).message}")
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = stringResource(if (isSignUp) R.string.sign_up else R.string.sign_in),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocusRequester),
                enabled = !isLoading,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester),
                enabled = !isLoading,
                singleLine = true
            )

            if (isSignUp) {
                Spacer(modifier = Modifier.height(16.dp))

                // Confirm password field
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(confirmPasswordFocusRequester),
                    enabled = !isLoading,
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sign in/up button
            Button(
                onClick = {
                    scope.launch {
                        if (isSignUp) {
                            viewModel.signUpWithEmail(email, password, confirmPassword)
                        } else {
                            viewModel.signInWithEmail(email, password)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = isFormValid && !isLoading && !isOffline
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(if (isSignUp) R.string.create_account else R.string.sign_in),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle sign in/up
            TextButton(
                onClick = {
                    isSignUp = !isSignUp
                    confirmPassword = ""
                },
                enabled = !isLoading
            ) {
                Text(
                    text = stringResource(
                        if (isSignUp) R.string.already_have_account
                        else R.string.dont_have_account
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.or),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Google Sign In button
            OutlinedButton(
                onClick = {
                    if (isOffline) {
                        scope.launch {
                            viewModel.setAuthResult(AuthResult.Error("Google Sign-In requires internet connection"))
                        }
                    } else {
                        try {
                            FirebaseCrashlytics.getInstance().log("Initiating Google Sign-In flow")
                            Timber.d("Initiating Google Sign-In flow with client ID: ${context.getString(R.string.default_web_client_id)}")
                            isGoogleSignInInProgress.value = true
                            googleSignInClient.signOut().addOnCompleteListener {
                                // Clear any previous sign-in state
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        } catch (e: Exception) {
                            Timber.e("Error launching Google Sign-In: ${e.message}")
                            FirebaseCrashlytics.getInstance().recordException(e)
                            scope.launch {
                                viewModel.setAuthResult(AuthResult.Error("Error launching Google Sign-In: ${e.message}"))
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading && !isOffline && !isGoogleSignInInProgress.value // Fixed
            ) {
                Text(
                    text = stringResource(R.string.sign_in_with_google),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guest mode button - only show if user is not already a guest
            val authState by viewModel.authState.collectAsState()
            val isGuest = authState is AuthState.Authenticated && (authState as AuthState.Authenticated).isAnonymous
            
            if (!isGuest) {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.signInAnonymously()
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text(
                        text = stringResource(R.string.continue_as_guest),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Error message
            if (authResult is AuthResult.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (authResult as AuthResult.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Offline message
            if (isOffline) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.error_no_internet),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}