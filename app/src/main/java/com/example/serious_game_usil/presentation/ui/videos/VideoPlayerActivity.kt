package com.example.serious_game_usil.presentation.ui.videos

import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityVideoPlayerBinding
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private var youTubePlayer: YouTubePlayer? = null
    private var isInPiPMode = false
    private var downloadId: Long = -1

    companion object {
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_VIDEO_CHANNEL = "extra_video_channel"
        const val EXTRA_VIDEO_CATEGORY = "extra_video_category"
        const val EXTRA_VIDEO_DESCRIPTION = "extra_video_description"
        const val EXTRA_VIDEO_URL = "extra_video_url"
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                binding.downloadProgressLayout.visibility = View.GONE
                Toast.makeText(
                    this@VideoPlayerActivity,
                    "Video descargado exitosamente",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Registrar receiver para descarga completa
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                downloadCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                downloadCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        setupVideoPlayer()
        setupUI()
    }

    private fun setupVideoPlayer() {
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: return

        lifecycle.addObserver(binding.youtubePlayerView)

        val options = IFramePlayerOptions.Builder()
            .controls(1) // Mostrar controles
            .rel(0) // No mostrar videos relacionados
            .ccLoadPolicy(1) // Mostrar subtítulos si están disponibles
            .ivLoadPolicy(3) // Ocultar anotaciones
            .build()

        binding.youtubePlayerView.enableAutomaticInitialization = false

        binding.youtubePlayerView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                this@VideoPlayerActivity.youTubePlayer = youTubePlayer
                youTubePlayer.loadVideo(videoId, 0f)
            }
        }, options)
    }

    private fun setupUI() {
        // Cargar datos del video
        binding.videoTitle.text = intent.getStringExtra(EXTRA_VIDEO_TITLE)
        binding.videoChannel.text = intent.getStringExtra(EXTRA_VIDEO_CHANNEL)
        binding.videoCategory.text = intent.getStringExtra(EXTRA_VIDEO_CATEGORY)
        binding.videoDescription.text = intent.getStringExtra(EXTRA_VIDEO_DESCRIPTION)

        // Botón cerrar
        binding.fabClose.setOnClickListener {
            onBackPressed()
        }

        // Botón descargar
        binding.btnDownload.setOnClickListener {
            downloadVideo()
        }

        // Botón Picture-in-Picture
        binding.btnPictureInPicture.setOnClickListener {
            enterPictureInPicture()
        }
    }

    private fun downloadVideo() {
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: return
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "video"

        try {
            // Nota: YouTube no permite descargas directas por sus términos de servicio
            // Esta es una implementación educativa que redirige a YouTube
            Toast.makeText(
                this,
                "Por políticas de YouTube, no se pueden descargar videos directamente.\n" +
                        "Abriendo YouTube para que puedas guardarlo offline...",
                Toast.LENGTH_LONG
            ).show()

            // Abrir en YouTube app para guardar offline
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
            intent.setPackage("com.google.android.youtube")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Si no tiene YouTube app, abrir en navegador
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                startActivity(webIntent)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error al procesar descarga: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()

                enterPictureInPictureMode(params)
                isInPiPMode = true

                Toast.makeText(
                    this,
                    "Modo mini reproductor activado. Puedes navegar por otras pantallas.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Tu dispositivo no soporta Picture-in-Picture",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                this,
                "Picture-in-Picture requiere Android 8.0 o superior",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            // Ocultar UI cuando está en modo PiP
            binding.contentScrollView.visibility = View.GONE
            binding.fabClose.visibility = View.GONE
            binding.downloadProgressLayout.visibility = View.GONE
        } else {
            // Mostrar UI cuando sale de modo PiP
            binding.contentScrollView.visibility = View.VISIBLE
            binding.fabClose.visibility = View.VISIBLE
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-entrar en PiP cuando el usuario presiona Home
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isInPiPMode) {
                enterPictureInPicture()
            }
        }
    }

    override fun onBackPressed() {
        // Simplemente volver atrás
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadCompleteReceiver)
        } catch (e: Exception) {
            // Receiver already unregistered
        }
        binding.youtubePlayerView.release()
    }
}
