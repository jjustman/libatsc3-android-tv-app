package com.nextgenbroadcast.mobile.tvandroid.view

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.player.Atsc3MediaPlayer
import com.nextgenbroadcast.mobile.player.MMTConstants

typealias OnStateChangedListener = (PlaybackState) -> Unit
typealias OnPlaybackChangeListener = (position: Long, rate: Float) -> Unit

class ReceiverMediaPlayer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    private val atsc3Player = Atsc3MediaPlayer(context).apply {
        resetWhenLostAudioFocus = false
    }
    private lateinit var mediaUpdateTimer: UpdateTimer

    private var buffering = false

    private var onStateListener: OnStateChangedListener? = null
    private var onPlaybackChangeListener: OnPlaybackChangeListener? = null

    val playbackPosition
        get() = player?.currentPosition ?: 0

    val playbackSpeed: Float
        get() = player?.playbackParameters?.speed ?: 0f

    val playbackState: PlaybackState
        get() = atsc3Player.playbackState

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (isInEditMode) return

        mediaUpdateTimer = UpdateTimer(MEDIA_TIME_UPDATE_DELAY) {
            onPlaybackChangeListener?.invoke(atsc3Player.playbackPosition, atsc3Player.playbackSpeed)
        }

        atsc3Player.setListener(object : Atsc3MediaPlayer.EventListener {
            override fun onPlayerStateChanged(state: PlaybackState) {
                if (state == PlaybackState.PLAYING) {
                    keepScreenOn = true
                    mediaUpdateTimer.start()
                } else {
                    keepScreenOn = false
                    mediaUpdateTimer.stop()
                }

                onStateListener?.invoke(state)
            }

            override fun onPlayerError(error: Exception) {
                Log.d(TAG, error.message ?: "")
            }

            override fun onPlaybackSpeedChanged(speed: Float) {
                // will be updated with position in timer
            }
        })
    }

    fun setOnStateChangedListener(listener: OnStateChangedListener) {
        this.onStateListener = listener
    }

    fun setOnPlaybackChangedListener(listener: OnPlaybackChangeListener) {
        this.onPlaybackChangeListener = listener
    }

    fun getTrackSelector(): DefaultTrackSelector? {
        return atsc3Player.trackSelector
    }

    fun play(mediaUri: Uri) {
        // prevent player reinitialization with same media
        if (atsc3Player.lastMediaUri == mediaUri && (atsc3Player.isPlaying || atsc3Player.isPaused)) return

        val mimeType = context.contentResolver.getType(mediaUri)
        if (mimeType == MMTConstants.MIME_MMT_AUDIO) {
            stopAndClear()
            return
        }

        atsc3Player.play(mediaUri)
        player = atsc3Player.player?.also {
            it.addListener(object : Player.EventListener {
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    updateBufferingState(playbackState == Player.STATE_BUFFERING)
                }
            })
        }
    }

    fun tryReplay() {
        if (atsc3Player.isPlaying) return

        atsc3Player.tryReplay()
        // ensure we still observing correct player
        player = atsc3Player.player
    }

    fun pause() {
        atsc3Player.pause()
    }

    fun stop() {
        atsc3Player.stop()
    }

    fun stopAndClear() {
        atsc3Player.reset()
        player = null
        atsc3Player.clearSavedState()
    }

    private fun updateBufferingState(isBuffering: Boolean) {
        if (isBuffering) {
            if (!buffering) {
                buffering = true
                postDelayed(enableBufferingProgress, 500)
            }
        } else {
            buffering = false
            removeCallbacks(enableBufferingProgress)
            setShowBuffering(SHOW_BUFFERING_NEVER)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        removeCallbacks(enableBufferingProgress)
    }

    private val enableBufferingProgress = Runnable {
        setShowBuffering(SHOW_BUFFERING_ALWAYS)
    }

    companion object {
        val TAG: String = ReceiverMediaPlayer::class.java.simpleName

        private const val MEDIA_TIME_UPDATE_DELAY = 500L
    }
}