package com.example.serious_game_usil.presentation.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.presentation.ui.login.BiometricLoginHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

class BiometricSettingsActivity : AppCompatActivity() {

    private lateinit var biometricHelper: BiometricLoginHelper
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var statusCard: CardView
    private lateinit var statusText: TextView
    private lateinit var finger1Card: CardView
    private lateinit var finger1Status: TextView
    private lateinit var finger1Button: Button
    private lateinit var finger2Card: CardView
    private lateinit var finger2Status: TextView
    private lateinit var finger2Button: Button
    private lateinit var attemptsText: TextView

    private var registeredFingers = mutableSetOf<Int>()
    private var authToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biometric_settings)

        biometricHelper = BiometricLoginHelper(this)
        authToken = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
            .getString("auth_token", null)

        initViews()
        setupToolbar()
        loadBiometricStatus()
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        statusCard = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        finger1Card = findViewById(R.id.finger1Card)
        finger1Status = findViewById(R.id.finger1Status)
        finger1Button = findViewById(R.id.finger1Button)
        finger2Card = findViewById(R.id.finger2Card)
        finger2Status = findViewById(R.id.finger2Status)
        finger2Button = findViewById(R.id.finger2Button)
        attemptsText = findViewById(R.id.attemptsText)

        finger1Button.setOnClickListener {
            handleFingerAction(1)
        }

        finger2Button.setOnClickListener {
            handleFingerAction(2)
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configuración Biométrica"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadBiometricStatus() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE

            authToken?.let { token ->
                val status = biometricHelper.checkBiometricStatus(token)

                status?.let {
                    registeredFingers.clear()

                    if (it.registeredFingerprints > 0) {
                        registeredFingers.add(1)
                    }
                    if (it.registeredFingerprints > 1) {
                        registeredFingers.add(2)
                    }

                    updateUI(it)
                }
            }

            progressBar.visibility = View.GONE
        }
    }

    private fun updateUI(status: BiometricLoginHelper.BiometricStatus) {
        if (status.isLocked) {
            statusText.text = "Estado: BLOQUEADO (${status.lockoutTimeRemaining}s restantes)"
            statusCard.setCardBackgroundColor(getColor(R.color.error_color))
            finger1Button.isEnabled = false
            finger2Button.isEnabled = false
        } else {
            statusText.text = "Estado: ACTIVO"
            statusCard.setCardBackgroundColor(getColor(R.color.success_color))
        }

        attemptsText.text = "Intentos restantes: ${status.remainingAttempts}/5"

        updateFingerCard(1)
        updateFingerCard(2)
    }

    private fun updateFingerCard(fingerIndex: Int) {
        val isRegistered = registeredFingers.contains(fingerIndex)

        when (fingerIndex) {
            1 -> {
                finger1Status.text = if (isRegistered) "Registrada" else "No registrada"
                finger1Button.text = if (isRegistered) "Eliminar" else "Registrar"
                finger1Card.setCardBackgroundColor(
                    getColor(if (isRegistered) R.color.primary_light else R.color.card_background)
                )
            }
            2 -> {
                finger2Status.text = if (isRegistered) "Registrada" else "No registrada"
                finger2Button.text = if (isRegistered) "Eliminar" else "Registrar"
                finger2Card.setCardBackgroundColor(
                    getColor(if (isRegistered) R.color.primary_light else R.color.card_background)
                )
                finger2Button.isEnabled = registeredFingers.contains(1) || isRegistered
            }
        }
    }

    private fun handleFingerAction(fingerIndex: Int) {
        if (registeredFingers.contains(fingerIndex)) {
            showDeleteConfirmation(fingerIndex)
        } else {
            registerFingerprint(fingerIndex)
        }
    }

    private fun registerFingerprint(fingerIndex: Int) {
        authToken?.let { token ->
            biometricHelper.showBiometricPromptForRegistration(
                token = token,
                fingerIndex = fingerIndex,
                onSuccess = { message ->
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    registeredFingers.add(fingerIndex)
                    updateFingerCard(fingerIndex)
                    loadBiometricStatus()
                },
                onError = { error ->
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun deleteFingerprint(fingerIndex: Int) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE

            authToken?.let { token ->
                biometricHelper.deleteFingerprint(token, fingerIndex).fold(
                    onSuccess = { message ->
                        Toast.makeText(this@BiometricSettingsActivity, message, Toast.LENGTH_SHORT).show()
                        registeredFingers.remove(fingerIndex)
                        if (fingerIndex == 1) {
                            registeredFingers.remove(2)
                        }
                        updateFingerCard(1)
                        updateFingerCard(2)
                        loadBiometricStatus()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@BiometricSettingsActivity,
                            "Error: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }

            progressBar.visibility = View.GONE
        }
    }

    private fun showDeleteConfirmation(fingerIndex: Int) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Huella")
            .setMessage("¿Está seguro que desea eliminar la huella del dedo ${if(fingerIndex == 1) "índice" else "pulgar"}?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteFingerprint(fingerIndex)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}