package com.example.serious_game_usil.presentation.ui.progress

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityProgressDetailBinding
import com.google.android.material.tabs.TabLayoutMediator

class ProgressDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressDetailBinding
    private lateinit var progressViewModel: ProgressViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityProgressDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupViewModel()
        setupViewPager()
        loadProgressData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Progreso Terapéutico"
    }

    private fun setupViewModel() {
        progressViewModel = ViewModelProvider(this)[ProgressViewModel::class.java]
        
        progressViewModel.allChildrenProgress.observe(this) { progressList ->
            progressList?.let {
                updateUI(it)
            }
        }
        
        progressViewModel.isLoading.observe(this) { isLoading ->
            // Mostrar/ocultar indicador de carga
            binding.progressBar.visibility = if (isLoading) 
                android.view.View.VISIBLE else android.view.View.GONE
        }
        
        progressViewModel.error.observe(this) { error ->
            error?.let {
                // Mostrar mensaje de error
                binding.errorText.text = it
                binding.errorText.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun setupViewPager() {
        val adapter = ProgressPagerAdapter()
        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Resumen General"
                1 -> "Por Niño"
                2 -> "Estadísticas"
                else -> "Tab $position"
            }
        }.attach()
    }

    private fun loadProgressData() {
        progressViewModel.loadAllChildrenProgress()
    }

    private fun updateUI(progressList: List<com.example.serious_game_usil.data.ThreeMonthComparison>) {
        binding.errorText.visibility = android.view.View.GONE
        
        if (progressList.isEmpty()) {
            binding.emptyStateText.visibility = android.view.View.VISIBLE
            binding.emptyStateText.text = "No hay datos de progreso disponibles.\nComienza registrando sesiones terapéuticas."
        } else {
            binding.emptyStateText.visibility = android.view.View.GONE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    inner class ProgressPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ProgressSummaryFragment() // Resumen general
                1 -> ProgressChildrenListFragment() // Lista por niño
                2 -> ProgressStatsFragment() // Estadísticas
                else -> ProgressSummaryFragment()
            }
        }
    }
}

// Fragmentos placeholder - se pueden implementar después
class ProgressSummaryFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return ThreeMonthComparisonFragment().onCreateView(inflater, container, savedInstanceState)
    }
}

class ProgressChildrenListFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return inflater.inflate(R.layout.fragment_progress_children_list, container, false)
    }
}

class ProgressStatsFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return inflater.inflate(R.layout.fragment_progress_stats, container, false)
    }
}