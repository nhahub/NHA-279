package com.example.movieapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.movieapp.R
import com.example.movieapp.databinding.ActivityAuthBinding
import com.example.movieapp.ui.main.MainActivity
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupClickListeners()
        observeAuthState()
    }

    private fun setupClickListeners() {
        binding.btnEmailLogin.setOnClickListener {
            viewModel.signInWithEmail("", "")
            Toast.makeText(this, R.string.auth_email_coming_soon, Toast.LENGTH_SHORT).show()
        }

        binding.btnGoogleLogin.setOnClickListener {
            viewModel.signInWithGoogle("")
            Toast.makeText(this, R.string.auth_google_coming_soon, Toast.LENGTH_SHORT).show()
        }

        binding.btnGuestLogin.setOnClickListener {
            viewModel.signInAsGuest()
        }
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { state ->
                    when (state) {
                        is AuthState.Idle -> {
                            // Enable all buttons
                            binding.btnEmailLogin.isEnabled = true
                            binding.btnGoogleLogin.isEnabled = true
                            binding.btnGuestLogin.isEnabled = true
                        }
                        is AuthState.Loading -> {
                            Toast.makeText(this@AuthActivity, R.string.auth_loading, Toast.LENGTH_SHORT).show()
                            // Disable all buttons to prevent multiple clicks
                            binding.btnEmailLogin.isEnabled = false
                            binding.btnGoogleLogin.isEnabled = false
                            binding.btnGuestLogin.isEnabled = false
                        }
                        is AuthState.Success -> {
                            Toast.makeText(this@AuthActivity, R.string.auth_success, Toast.LENGTH_SHORT).show()
                            navigateToMainActivity()
                        }
                        is AuthState.Error -> {
                            Toast.makeText(this@AuthActivity, state.message, Toast.LENGTH_LONG).show()
                            // Re-enable buttons on error
                            binding.btnEmailLogin.isEnabled = true
                            binding.btnGoogleLogin.isEnabled = true
                            binding.btnGuestLogin.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}