package com.example.serious_game_usil.presentation.ui.caregivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.R

class CaregiverDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_CAREGIVER_ID = "caregiver_id"

        fun newIntent(context: Context, caregiverId: Int): Intent {
            return Intent(context, CaregiverDetailActivity::class.java).apply {
                putExtra(EXTRA_CAREGIVER_ID, caregiverId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_detail) // Temporal, reutilizando layout existente

        val caregiverId = intent.getIntExtra(EXTRA_CAREGIVER_ID, 0)

        // TODO: Implementar vista de detalle para cuidadores con pacientes asignados
        finish() // Por ahora solo cierra la actividad
    }
}