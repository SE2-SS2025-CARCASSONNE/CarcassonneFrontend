package at.se2_ss2025_gruppec.carcasonnefrontend

import android.content.Context
import android.media.MediaPlayer

object SoundManager {
    private var mediaPlayer: MediaPlayer? = null
    private var currentTrack: Int? = null

    var volume: Float = 0.5f
        private set

    var isMuted: Boolean = false
        private set

    fun playMusic(context: Context, musicResId: Int) {
        if (currentTrack == musicResId && mediaPlayer?.isPlaying == true) return

        stopMusic()

        mediaPlayer = MediaPlayer.create(context, musicResId).apply {
            isLooping = true
            setVolume(
                if (isMuted) 0f else volume,
                if (isMuted) 0f else volume
            )
            start()
        }

        currentTrack = musicResId
    }

    fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentTrack = null
    }

    fun pauseMusic() {
        mediaPlayer?.pause()
    }

    fun resumeMusic(context: Context) {
        currentTrack?.let { resId ->
            playMusic(context, resId)
        }
    }

    fun setVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        isMuted = volume == 0f
        mediaPlayer?.setVolume(volume, volume)
    }

    fun mute() {
        isMuted = true
        mediaPlayer?.setVolume(0f, 0f)
    }

    fun unmute() {
        isMuted = false
        mediaPlayer?.setVolume(volume, volume)
    }
}