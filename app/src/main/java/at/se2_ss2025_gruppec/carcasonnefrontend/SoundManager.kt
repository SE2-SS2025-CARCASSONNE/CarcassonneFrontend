package at.se2_ss2025_gruppec.carcasonnefrontend

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import at.se2_ss2025_gruppec.carcasonnefrontend.R
import at.se2_ss2025_gruppec.carcasonnefrontend.SoundManager.mediaPlayer

object SoundManager {
    private var mediaPlayer: MediaPlayer? = null
    private var currentTrack: Int? = null

    fun init(context: Context) {
        // start lobby music by default
        playMusic(context, R.raw.lobby_music)
    }

    fun playMusic(context: Context, musicResId: Int) {
        if (currentTrack == musicResId && mediaPlayer?.isPlaying == true) return
        stopMusic()
        mediaPlayer = MediaPlayer.create(context, musicResId).apply {
            isLooping = true
            setVolume(0.5f, 0.5f)
            start()
        }
        currentTrack = musicResId
    }
    fun pauseMusic() {
        mediaPlayer?.pause()
    }
    fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentTrack = null
    }
    fun setVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }
}
