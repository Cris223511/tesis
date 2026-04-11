package com.example.serious_game_usil.presentation.ui.videos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.BuildConfig
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.VideoCategory
import com.example.serious_game_usil.data.VideoEducativo
import com.example.serious_game_usil.databinding.ActivityVideosEducativosBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class VideosEducativosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideosEducativosBinding
    private lateinit var videosAdapter: VideosAdapter
    private var currentCategory = VideoCategory.TODOS
    private var allVideos = mutableListOf<VideoEducativo>()
    private var displayedVideos = mutableListOf<VideoEducativo>()
    private var isLoading = false
    private var currentPage = 0
    private var nextPageToken: String? = null

    // YouTube Data API Key - Cargada desde BuildConfig
    private val YOUTUBE_API_KEY = BuildConfig.YOUTUBE_API_KEY

    companion object {
        private const val VIDEOS_PER_PAGE = 5
        private const val MAX_RESULTS = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideosEducativosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupCategories()

        // Cargar videos iniciales
        loadVideosByCategory(VideoCategory.TODOS)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Videos Educativos"
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        videosAdapter = VideosAdapter { video ->
            openVideoPlayer(video)
        }

        binding.videosRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@VideosEducativosActivity)
            adapter = videosAdapter
            setHasFixedSize(true)

            // Scroll listener para paginación
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && displayedVideos.size < allVideos.size) {
                        loadMoreVideos()
                    }
                }
            })
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            private var searchRunnable: Runnable? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { binding.searchEditText.removeCallbacks(it) }
            }

            override fun afterTextChanged(s: Editable?) {
                searchRunnable = Runnable {
                    val query = s?.toString()?.trim()
                    if (query.isNullOrEmpty()) {
                        filterVideosByCategory(currentCategory)
                    } else {
                        searchVideos(query)
                    }
                }
                binding.searchEditText.postDelayed(searchRunnable, 500)
            }
        })
    }

    private fun setupCategories() {
        binding.chipGroupCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val selectedChip = findViewById<Chip>(checkedIds.first())
            currentCategory = when (selectedChip.id) {
                R.id.chipTodos -> VideoCategory.TODOS
                R.id.chipConducta -> VideoCategory.CONDUCTA
                R.id.chipComunicacion -> VideoCategory.COMUNICACION
                R.id.chipEmociones -> VideoCategory.EMOCIONES
                else -> VideoCategory.TODOS
            }

            binding.searchEditText.setText("")
            loadVideosByCategory(currentCategory)
        }
    }

    private fun loadVideosByCategory(category: VideoCategory) {
        if (isLoading) return

        showLoading(true)

        lifecycleScope.launch {
            try {
                val videos = fetchVideosFromYouTube(category.searchTerm)
                allVideos.clear()
                allVideos.addAll(videos.map { it.copy(category = category.displayName.uppercase()) })

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    if (videos.isEmpty()) {
                        showEmptyState(
                            title = "No se encontraron videos",
                            message = "No hay videos disponibles para esta categoría en este momento"
                        )
                    } else {
                        hideEmptyState()
                        // Mostrar solo los primeros 5 videos
                        displayedVideos.clear()
                        displayedVideos.addAll(allVideos.take(VIDEOS_PER_PAGE))
                        videosAdapter.submitList(displayedVideos.toList())
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showError("Error al cargar videos: ${e.message}")
                    showEmptyState(
                        title = "Error de conexión",
                        message = "No se pudieron cargar los videos. Verifica tu conexión a internet."
                    )
                }
            }
        }
    }

    private fun searchVideos(query: String) {
        if (query.length < 3) {
            filterVideosByCategory(currentCategory)
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            try {
                val searchQuery = "$query TEA autismo"
                val videos = fetchVideosFromYouTube(searchQuery)

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    if (videos.isEmpty()) {
                        showEmptyState(
                            title = "No se encontraron coincidencias",
                            message = "No encontramos videos relacionados con \"$query\". Intenta con otros términos."
                        )
                    } else {
                        hideEmptyState()
                        videosAdapter.submitList(videos)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showError("Error en la búsqueda: ${e.message}")
                    showEmptyState(
                        title = "Error de búsqueda",
                        message = "No se pudo completar la búsqueda. Verifica tu conexión."
                    )
                }
            }
        }
    }

    private fun filterVideosByCategory(category: VideoCategory) {
        val filtered = allVideos.filter {
            it.category.equals(category.displayName, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            showEmptyState(
                title = "No hay videos",
                message = "No hay videos en esta categoría"
            )
        } else {
            hideEmptyState()
            videosAdapter.submitList(filtered)
        }
    }

    private suspend fun fetchVideosFromYouTube(searchQuery: String): List<VideoEducativo> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
            val urlString = "https://www.googleapis.com/youtube/v3/search?" +
                    "part=snippet" +
                    "&q=$encodedQuery" +
                    "&type=video" +
                    "&maxResults=$MAX_RESULTS" +
                    "&relevanceLanguage=es" +
                    "&safeSearch=strict" +
                    "&key=$YOUTUBE_API_KEY"

            val response = URL(urlString).readText()
            val jsonObject = JSONObject(response)
            val items = jsonObject.optJSONArray("items") ?: return@withContext emptyList()

            val videos = mutableListOf<VideoEducativo>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.getJSONObject("id").getString("videoId")
                val snippet = item.getJSONObject("snippet")

                val video = VideoEducativo(
                    id = id,
                    title = snippet.getString("title"),
                    thumbnailUrl = snippet.getJSONObject("thumbnails")
                        .getJSONObject("medium")
                        .getString("url"),
                    channelTitle = snippet.getString("channelTitle"),
                    videoUrl = "https://www.youtube.com/watch?v=$id",
                    description = snippet.optString("description", "")
                )
                videos.add(video)
            }

            videos
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun openVideoPlayer(video: VideoEducativo) {
        val intent = Intent(this, VideoPlayerActivitySimple::class.java).apply {
            putExtra(VideoPlayerActivitySimple.EXTRA_VIDEO_ID, video.id)
            putExtra(VideoPlayerActivitySimple.EXTRA_VIDEO_TITLE, video.title)
            putExtra(VideoPlayerActivitySimple.EXTRA_VIDEO_CHANNEL, video.channelTitle)
            putExtra(VideoPlayerActivitySimple.EXTRA_VIDEO_CATEGORY, video.category)
            putExtra(VideoPlayerActivitySimple.EXTRA_VIDEO_DESCRIPTION, video.description)
            putExtra(VideoPlayerActivitySimple.EXTRA_VIDEO_URL, video.videoUrl)
        }
        startActivity(intent)
    }

    private fun loadMoreVideos() {
        val startIndex = displayedVideos.size
        val endIndex = minOf(startIndex + VIDEOS_PER_PAGE, allVideos.size)

        if (startIndex < allVideos.size) {
            val newVideos = allVideos.subList(startIndex, endIndex)
            displayedVideos.addAll(newVideos)
            videosAdapter.submitList(displayedVideos.toList())

            Toast.makeText(
                this,
                "Cargados ${newVideos.size} videos más",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showLoading(show: Boolean) {
        isLoading = show
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.videosRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showEmptyState(title: String, message: String) {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateTitle.text = title
        binding.emptyStateMessage.text = message
        binding.videosRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.videosRecyclerView.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
