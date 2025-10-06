package com.example.serious_game_usil.presentation.ui.videos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.databinding.ActivityVideoPlayerSimpleBinding

class VideoPlayerActivitySimple : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerSimpleBinding

    companion object {
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_VIDEO_CHANNEL = "extra_video_channel"
        const val EXTRA_VIDEO_CATEGORY = "extra_video_category"
        const val EXTRA_VIDEO_DESCRIPTION = "extra_video_description"
        const val EXTRA_VIDEO_URL = "extra_video_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerSimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupVideoPlayer()
        setupUI()
    }

    private fun setupVideoPlayer() {
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: return

        // Configurar WebView
        binding.webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        binding.webView.webChromeClient = WebChromeClient()
        binding.webView.webViewClient = WebViewClient()

        // Cargar video de YouTube embebido
        val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
        val htmlData = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 0; background: #000; }
                    iframe {
                        width: 100vw;
                        height: 100vh;
                        border: none;
                    }
                </style>
            </head>
            <body>
                <iframe
                    src="$embedUrl"
                    frameborder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                    allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()

        binding.webView.loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)
    }

    private fun setupUI() {
        // Cargar datos del video
        binding.videoTitle.text = intent.getStringExtra(EXTRA_VIDEO_TITLE)
        binding.videoChannel.text = intent.getStringExtra(EXTRA_VIDEO_CHANNEL)
        binding.videoCategory.text = intent.getStringExtra(EXTRA_VIDEO_CATEGORY)

        val description = intent.getStringExtra(EXTRA_VIDEO_DESCRIPTION)
        binding.videoDescription.text = if (description.isNullOrEmpty()) {
            "Video educativo sobre el Trastorno del Espectro Autista (TEA)"
        } else {
            description
        }

        // Botón cerrar
        binding.fabClose.setOnClickListener {
            finish()
        }

        // Botón abrir en YouTube
        binding.btnOpenYouTube.setOnClickListener {
            openInYouTube()
        }

        // Botón compartir
        binding.btnShare.setOnClickListener {
            shareVideo()
        }
    }

    private fun openInYouTube() {
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: return
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: return

        try {
            // Intentar abrir en la app de YouTube
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
            appIntent.setPackage("com.google.android.youtube")
            startActivity(appIntent)
        } catch (e: Exception) {
            // Si no funciona, abrir en el navegador
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
            startActivity(webIntent)
        }
    }

    private fun shareVideo() {
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: return
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video educativo TEA"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, videoTitle)
            putExtra(Intent.EXTRA_TEXT, "Mira este video educativo sobre TEA: $videoUrl")
        }

        startActivity(Intent.createChooser(shareIntent, "Compartir video"))
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
    }
}
