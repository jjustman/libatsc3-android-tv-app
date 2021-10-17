package com.nextgenbroadcast.mobile.tvandroid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.Task
import com.nextgenbroadcast.mobile.core.atsc3.PhyInfoConstants
import com.nextgenbroadcast.mobile.core.dev.service.binder.IServiceBinder
import com.nextgenbroadcast.mobile.core.getApkBaseServicePackage
import com.nextgenbroadcast.mobile.core.media.MediaSessionConstants
import com.nextgenbroadcast.mobile.core.model.*
import com.nextgenbroadcast.mobile.tvandroid.databinding.ActivityMainBinding
import com.nextgenbroadcast.mobile.tvandroid.entity.*
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.IServiceGuideManager
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.ServiceGuideManagerImpl
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.ServiceGuideView
import com.nextgenbroadcast.mobile.tvandroid.utils.SwipeGestureListener
import com.nextgenbroadcast.mobile.tvandroid.utils.mapWith
import com.nextgenbroadcast.mobile.tvandroid.view.*
import com.nextgenbroadcast.mobile.view.AboutDialog
import com.nextgenbroadcast.mobile.view.TrackSelectionDialog
import com.nextgenbroadcast.mobile.view.UserAgentView
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.TimeUnit


class MainActivity : Atsc3Activity(), ServiceGuideView.OnServiceListener {
    private val sources = mutableListOf<RouteUrl>()
    private val selectedServiceTitle = MutableLiveData<String?>()
    private val scanningTime = MutableLiveData<Long>()
    private val stateLiveData = MutableLiveData<ReceiverState>()
    private val rect = Rect()

    private val hasFeaturePIP: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private lateinit var serviceGuideManager: IServiceGuideManager
    private lateinit var progressUpdateTimer: UpdateTimer
    private lateinit var serviceGuideGestureDetector: GestureDetector
    private lateinit var sourceAdapter: ArrayAdapter<String>
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var receiverContentResolver: ReceiverContentResolver

    private var currentAppData: AppData? = null
    private var isBAVisible: Boolean = false
    private var isShowingTrackSelectionDialog = false
    private var countDownTimer: CountDownTimer? = null
    private var requestedPlaybackState = PlaybackState.IDLE

    override fun onBind(binder: IServiceBinder) {
        // enable Telemetry by default
        binder.controllerPresenter?.setTelemetryEnabled(true)
    }

    override fun onUnbind() {
        // do nothing
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        when (intent.action) {
            ACTION_OPEN_TUNE_DIALOG -> openTuneDialog(intent.getIntExtra(EXTRA_EXTRA, 0))
            ACTION_TUNE -> startTune(intent.getIntExtra(EXTRA_FREQUENCY, 0))
            ACTION_SCAN -> startScanning()
            else -> tryOpenPcapFile(intent)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        receiverContentResolver = ReceiverContentResolver(applicationContext)
        appUpdateManager = AppUpdateManagerFactory.create(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        progressUpdateTimer = UpdateTimer(PROGRESS_UPDATE_DELAY) {
            val text = binding.loadingProgressIndicator.text.toString()
            binding.loadingProgressIndicator.text = if (text.length < 3) {
                "$text."
            } else {
                ""
            }
        }

        serviceGuideGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                binding.mediaControlsContainer.getHitRect(rect)
                if (rect.contains(e.x.toInt(), e.y.toInt())) {
                    switchMediaControlsVisibility()
                }
                return false
            }

            override fun onShowPress(e: MotionEvent?) {
                // this works for motion layout click
                binding.motionLayout.animateIndicator()
            }

            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                // this works for BA (WebView) click
                if (!isBAVisible) {
                    binding.motionLayout.animateIndicator()
                }
                return false
            }
        })

        binding.baView.captureContentVisibility = true
        binding.baView.isContentVisible.observe(this) { visible ->
            isBAVisible = visible
            binding.motionLayout.lockBottomView = visible
            if (!visible) {
                binding.root.requestFocusFromTouch()
            }
        }

        initSources()
        initUserAgent()

        val authority = getString(R.string.nextgenServicesGuideProvider)
        serviceGuideManager = ServiceGuideManagerImpl(authority, contentResolver).also {
            binding.serviceGuideView.setDataManger(it)
        }
        binding.serviceGuideView.setServiceListener(this)

        binding.settingBtn.setOnClickListener {
            showPopupSettingsMenu(it)
        }

        binding.receiverMediaPlayer.setOnStateChangedListener { state ->
            updatePlayPauseButtonDrawable(state != PlaybackState.PLAYING)
            receiverContentResolver.publishPlayerState(state, binding.receiverMediaPlayer.playbackPosition, binding.receiverMediaPlayer.playbackSpeed)
        }
        binding.receiverMediaPlayer.setOnPlaybackChangedListener { position, rate ->
            receiverContentResolver.publishPlayerState(binding.receiverMediaPlayer.playbackState, position, rate)
        }

        binding.playerPlayPauseBtn.setOnClickListener {
            if (binding.receiverMediaPlayer.playbackState == PlaybackState.PLAYING) {
                binding.receiverMediaPlayer.pause()
                receiverContentResolver.applyPlayerState(PlaybackState.PAUSED)
            } else {
                binding.receiverMediaPlayer.tryReplay()
                receiverContentResolver.applyPlayerState(PlaybackState.PLAYING)
            }
        }

        binding.cancelScanBtn.setOnClickListener {
            receiverContentResolver.tune(-1)
        }

        stateLiveData.mapWith(selectedServiceTitle, scanningTime) { (receiverState, title, scanningTime) ->
            if (receiverState == null || receiverState.state == ReceiverState.State.IDLE) {
                getString(R.string.service_status_awaiting)
            } else if (receiverState.state == ReceiverState.State.SCANNING) {
                val fullTimeInMilliSeconds = scanningTime ?: 0
                val timeInMinutes = TimeUnit.MILLISECONDS.toMinutes(fullTimeInMilliSeconds)
                val sec = formatSecondsToString(TimeUnit.MILLISECONDS.toSeconds(fullTimeInMilliSeconds) - TimeUnit.MINUTES.toSeconds(timeInMinutes))
                getString(R.string.service_status_scanning, timeInMinutes, sec)
            } else if (title == null) {
                getString(R.string.service_status_tuning)
            } else {
                getString(R.string.service_status_buffering, title)
            }
        }.observe(this, { state ->
            binding.loadingProgressTitle.text = state
        })

        ReceiverContentResolver.resetPlayerState(this)

        // ignore if activity was restored
        if (savedInstanceState == null) {
            tryOpenPcapFile(intent)
        }

        binding.root.requestFocusFromTouch()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        with(binding) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isBAVisible) {
                        baView.actionExit()
                        return true
                    } else {
                        when (motionLayout.state) {
                            ReceiverMotionLayout.CLOSED,
                            ReceiverMotionLayout.IN_MIDDLE -> {
                                motionLayout.gotoState(ReceiverMotionLayout.IN_END)
                                serviceGuideGrid.requestFocus()
                                return true
                            }
                            ReceiverMotionLayout.IN_END -> {
                            }
                            ReceiverMotionLayout.IN_TOP -> {
                            }
                            ReceiverMotionLayout.NEUTRAL -> {
                            }
                        }
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isBAVisible) {
                        baView.actionEnter()
                        return true
                    } else {
                        when (motionLayout.state) {
                            ReceiverMotionLayout.CLOSED -> {
                                baView.actionEnter()
                                baView.requestFocus()
                                return true
                            }
                            ReceiverMotionLayout.IN_MIDDLE -> {
                                motionLayout.gotoState(ReceiverMotionLayout.CLOSED)
                                return true
                            }
                            ReceiverMotionLayout.IN_END -> {
                            }
                            ReceiverMotionLayout.IN_TOP -> {
                            }
                            ReceiverMotionLayout.NEUTRAL -> {
                            }
                        }
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!isBAVisible) {
                        when (motionLayout.state) {
                            ReceiverMotionLayout.CLOSED -> {
                                motionLayout.gotoState(ReceiverMotionLayout.IN_TOP)
                                sourcesList.requestFocus()
                                return true
                            }
                            ReceiverMotionLayout.IN_MIDDLE -> {
                            }
                            ReceiverMotionLayout.IN_END -> {
                            }
                            ReceiverMotionLayout.IN_TOP -> {
                            }
                            ReceiverMotionLayout.NEUTRAL -> {
                            }
                        }
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!isBAVisible) {
                        when (motionLayout.state) {
                            ReceiverMotionLayout.CLOSED -> {
                            }
                            ReceiverMotionLayout.IN_MIDDLE,
                            ReceiverMotionLayout.IN_END -> {
                            }
                            ReceiverMotionLayout.IN_TOP -> {
                            }
                            ReceiverMotionLayout.NEUTRAL -> {
                            }
                        }
                    }
                }

                KeyEvent.KEYCODE_BACK -> {
                    if (isBAVisible) {
                        baView.actionExit()
                        //TODO: this check works incorrect
                        //if (!baView.checkContentVisible()) {
                            root.requestFocusFromTouch()
                        //}
                        return true
                    } else if (motionLayout.state != ReceiverMotionLayout.CLOSED) {
                        motionLayout.closeAllMenu()
                        root.requestFocusFromTouch()
                        return true
                    }
                }
            }
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            setBAAvailability(false)
        } else {
            if(isBAvailable()) {
                setBAAvailability(true)
                receiverContentResolver.queryPlayerData()?.let { (_, params) ->
                    updateRMPLayout(params.x, params.y, params.scale)
                }
            }
        }
    }

    private fun isBAvailable() = currentAppData?.isAvailable ?: false

    override fun onStart() {
        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)

        super.onStart()

        receiverContentResolver.register()
        subscribeReceiverData()

        checkForAppUpdates()
    }

    override fun onResume() {
        super.onResume()

        updatePlayPauseButtonDrawable(binding.receiverMediaPlayer.playbackState == PlaybackState.PLAYING)

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // If an in-app update is already running, resume the update.
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, APP_UPDATE_REQUEST_CODE)
            }
        }
    }

    override fun onStop() {
        super.onStop()

        receiverContentResolver.unregister()

        stopPlayback()
        stopScanningTimer()

        binding.root.viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusListener)
    }

    override fun onBackPressed() {
        when {
            binding.baView.checkContentVisible() -> binding.baView.actionExit()
            binding.motionLayout.state != ReceiverMotionLayout.CLOSED -> binding.motionLayout.closeAllMenu()
            else -> super.onBackPressed()
        }
    }

    override fun onUserLeaveHint() {
        mediaController?.playbackState?.let { playbackState ->
            val embedded = playbackState.extras?.getBoolean(MediaSessionConstants.MEDIA_PLAYBACK_EXTRA_EMBEDDED)
            if (hasFeaturePIP && playbackState.state == android.media.session.PlaybackState.STATE_PLAYING && embedded != true) {
                binding.motionLayout.closeAllMenu()
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().build()
                )
            }
        }
    }

    private fun setBAAvailability(available: Boolean) {
        binding.baView.isVisible = available
        if (!available) {
            updateRMPLayout(1f, 1f, 1f)
        }
    }

    override fun onChannelSelect(service: AVService) {
        selectService(service)
    }

    override fun onContentSelected(name: String, description: String, info: String, previewUrl: String?) {
        binding.serviceNameView.text = name
        binding.serviceDescriptionView.text = description
        binding.serviceInfoView.text = info
        binding.programThumbnail.setUrl(previewUrl)
    }

    private fun subscribeReceiverData() {
        receiverContentResolver.observeRouteList { routes ->
            sources.clear()
            sources.addAll(routes)
            sourceAdapter.clear()
            sourceAdapter.addAll(routes.map { source -> source.title })
        }

        receiverContentResolver.observeServiceList { services ->
            serviceGuideManager.setActiveServices(services)
            trySelectDefaultService()
        }

        var skipFirst = true // skip on initialization
        receiverContentResolver.observeServiceSelection { service ->
            selectedServiceTitle.value = service?.shortName
            binding.serviceGuideView.setSelectedService(service?.globalId)

            if (!skipFirst) {
                skipFirst = false
                service?.let {
                    checkIsPartnerService(service)
                }
            }
        }

        receiverContentResolver.observeApplicationData { appData ->
            switchApplication(appData)
        }

        receiverContentResolver.observePlayerState { mediaUri, params, state ->
            Log.d(TAG, "Receiver Media Player changed - uri: $mediaUri, params: $params, state: $state")

            requestedPlaybackState = state

            if (!isInPictureInPictureMode) {
                updateRMPLayout(params.x, params.y, params.scale)
            }

            if (mediaUri != null) {
                startPlayback(mediaUri)
            } else {
                stopPlayback()
            }

            when(state) {
                PlaybackState.PAUSED -> binding.receiverMediaPlayer.pause()
                PlaybackState.PLAYING -> binding.receiverMediaPlayer.tryReplay()
                PlaybackState.IDLE -> binding.receiverMediaPlayer.stop()
            }
        }

        receiverContentResolver.observeReceiverState { state ->
            stateLiveData.value = state

            // exit PIP mode when Receiver is de-initialized
            if (state.state == ReceiverState.State.IDLE) {
                if (isInPictureInPictureMode) {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                }
            }

            if (state.state == ReceiverState.State.SCANNING) {
                binding.cancelScanBtn.visibility = View.VISIBLE
                val maxScanningTime = (state.configIndex + 1) * MAX_CHANNEL_SCANNING_TIME
                runScanningTimer(maxScanningTime)
            } else {
                binding.cancelScanBtn.visibility = View.INVISIBLE
                stopScanningTimer()
            }

            if (state.state == ReceiverState.State.READY) {
                trySelectDefaultService()
            }
        }
    }

    private fun showPopupSettingsMenu(v: View) {
        cancelMediaControlsHide()
        PopupMenu(v.context, v).apply {
            inflate(R.menu.settings_menu)
            if (binding.receiverMediaPlayer.player == null) {
                menu.findItem(R.id.menu_select_tracks)?.isEnabled = false
            }

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_select_tracks -> {
                        openSelectTracksDialog()
                        true
                    }
                    R.id.menu_tune -> {
                        openTuneDialog(receiverContentResolver.queryReceiverFrequency() ?: 0)
                        true
                    }
                    R.id.menu_dialog_about -> {
                        openInfoDialog()
                        true
                    }
                    else -> false
                }
            }

            setOnDismissListener {
                hideMediaControls()
            }
        }.show()
    }

    @SuppressLint("InflateParams")
    private fun openTuneDialog(frequencyMHz: Int) {
        TuneDialog(frequencyMHz).show(supportFragmentManager, TuneDialog::class.java.name)
    }

    private fun startTune(frequencyKHz: Int) {
        receiverContentResolver.tune(frequencyKHz * 1000)
    }

    private fun startScanning() {
        receiverContentResolver.tune(readDefaultFrequencies())
    }

    private fun readDefaultFrequencies(): ArrayList<Int> {
        val frequencyList = arrayListOf<Int>()
        val xrp = resources.getXml(R.xml.default_frequency_range)
        while (xrp.next() != XmlPullParser.END_DOCUMENT) {
            if (xrp.eventType != XmlPullParser.START_TAG) {
                continue
            }

            while (xrp.next() != XmlPullParser.END_TAG) {
                if (xrp.eventType != XmlPullParser.START_TAG) {
                    continue
                }

                while (xrp.next() != XmlPullParser.END_TAG) {
                    if (xrp.eventType == XmlPullParser.TEXT) {
                        xrp.text?.toIntOrNull()?.let { frequency ->
                            frequencyList.add(frequency * 1000)
                        }
                    } else {
                        check(xrp.eventType == XmlPullParser.START_TAG)
                        var depth = 1
                        while (depth != 0) {
                            when (xrp.next()) {
                                XmlPullParser.END_TAG -> depth--
                                XmlPullParser.START_TAG -> depth++
                            }
                        }
                    }
                }
            }
        }
        return frequencyList
    }

    private fun openInfoDialog() {
        val frequency = receiverContentResolver.queryReceiverFrequency()
        var sdkVersion: String? = null
        var firmwareVersion: String? = null
        var deviceType: String? = null
        var deviceId: String? = null

        receiverContentResolver.getPhyInfo()?.let { info ->
            sdkVersion = info[PhyInfoConstants.INFO_SDK_VERSION]
            firmwareVersion = info[PhyInfoConstants.INFO_FIRMWARE_VERSION]
            deviceType = info[PhyInfoConstants.INFO_PHY_TYPE]
            deviceId = info[PhyInfoConstants.INFO_DEVICE_ID]
        }

        AboutDialog(sdkVersion, firmwareVersion, deviceType, frequency, deviceId)
            .show(supportFragmentManager, null)

    }

    private fun openSelectTracksDialog() {
        val currentTrackSelection = binding.receiverMediaPlayer.player.currentTrackSelections
        val trackSelection = binding.receiverMediaPlayer.getTrackSelector()

        if (!isShowingTrackSelectionDialog
            && trackSelection != null
            && TrackSelectionDialog.willHaveContent(trackSelection)
        ) {

            isShowingTrackSelectionDialog = true
            val trackSelectionDialog = TrackSelectionDialog.createForTrackSelector(
                getString(R.string.menu_select_track_title),
                currentTrackSelection,
                trackSelection
            ) { _ -> isShowingTrackSelectionDialog = false }
            trackSelectionDialog.show(supportFragmentManager, null)
        }
    }

    private fun showQualityIcon(qualityState: SignalQualityState) {
        binding.antennaIndicator.removeCallbacks(hideAntennaIconRunnable)
        when (qualityState) {
            SignalQualityState.POOR -> {
                binding.antennaIndicator.visibility = View.VISIBLE
                binding.antennaIndicator.setColorFilter(getColor(R.color.antenna_red))
            }

            SignalQualityState.MEDIUM -> {
                binding.antennaIndicator.visibility = View.VISIBLE
                binding.antennaIndicator.setColorFilter(getColor(R.color.antenna_yellow))
                binding.antennaIndicator.postDelayed(hideAntennaIconRunnable, YELLOW_ICON_SHOW_TIME)
            }

            SignalQualityState.GOOD -> {
                binding.antennaIndicator.visibility = View.INVISIBLE
            }
        }
    }

    private fun switchMediaControlsVisibility() {
        if (binding.mediaControlsContainer.isVisible) {
            cancelMediaControlsHide()
            hideMediaControls()
        } else {
            showMediaControls()
        }
    }

    private fun updatePlayPauseButtonDrawable(isShowPause: Boolean) {
        binding.playerPlayPauseBtn.setImageResource(if (isShowPause) R.drawable.ic_exo_icon_play else R.drawable.ic_exo_icon_pause)
        cancelMediaControlsHide()
        startMediaControlsHideDelayed()
    }

    private fun showMediaControls() {
        if (!binding.mediaControlsContainer.isVisible) {
            binding.mediaControlsContainer.alpha = 0f
            binding.mediaControlsContainer.isVisible = true
            binding.mediaControlsContainer.animate()
                .setDuration(MEDIA_CONTROLS_ANIMATION_DURATION)
                .alpha(1f)
                .setListener(null)

            binding.playerPlayPauseBtn.isVisible = (binding.receiverMediaPlayer.player != null)

            cancelMediaControlsHide()
            startMediaControlsHideDelayed()
        }
    }

    private fun hideMediaControls() {
        binding.mediaControlsContainer.animate()
            .setDuration(MEDIA_CONTROLS_ANIMATION_DURATION)
            .alpha(0f)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    binding.mediaControlsContainer.visibility = View.INVISIBLE
                }
            })
    }

    private fun startMediaControlsHideDelayed() {
        binding.mediaControlsContainer.postDelayed(hideMediaControlsRunnable, MEDIA_CONTROLS_HIDE_DELAY)
    }

    private fun cancelMediaControlsHide() {
        binding.mediaControlsContainer.removeCallbacks(hideMediaControlsRunnable)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSources() {
        binding.sourcesList.adapter = ArrayAdapter<String>(this, R.layout.source_item).also {
            sourceAdapter = it
        }
        binding.sourcesList.setOnItemClickListener { _, _, position, _ ->
            selectSource(position)
            binding.motionLayout.closeAllMenu()
        }

        binding.motionLayout.setOnTouchListener { _, event ->
            serviceGuideGestureDetector.onTouchEvent(event)
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initUserAgent() {
        val swipeGD = GestureDetector(this, object : SwipeGestureListener() {
            override fun onClose() {
                Log.d(TAG, "Close BA")
                binding.baView.actionExit()
            }

            override fun onOpen() {
                Log.d(TAG, "Open BA")
                binding.baView.actionEnter()
            }
        })

        binding.baView.setOnTouchListener { _, motionEvent ->
            // BA should be responsive when ESG is closed only
            if (binding.motionLayout.state == ReceiverMotionLayout.CLOSED) {
                if (binding.mediaControlsContainer.isVisible && requestedPlaybackState != PlaybackState.IDLE) {
                    val offsetX = (binding.baView.left - binding.receiverMediaPlayer.left).toFloat()
                    val offsetY = (binding.baView.top - binding.receiverMediaPlayer.top).toFloat()

                    motionEvent.offsetLocation(offsetX, offsetY)
                    val eventHandled = binding.mediaControlsContainer.dispatchTouchEvent(motionEvent)
                    motionEvent.offsetLocation(-offsetX, -offsetY)

                    if (eventHandled) {
                        return@setOnTouchListener true
                    }
                }
                swipeGD.onTouchEvent(motionEvent)
                serviceGuideGestureDetector.onTouchEvent(motionEvent)

                return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }

        binding.baView.setListener(object : UserAgentView.IListener {
            override fun onLoadingError() {
                currentAppData = null
                binding.baView.unload()
                setBAAvailability(false)
                receiverContentResolver.publishApplicationState(ApplicationState.UNAVAILABLE)
            }

            override fun onClosed() {
                receiverContentResolver.publishApplicationState(ApplicationState.OPENED)
            }

            override fun onOpened() {
                receiverContentResolver.publishApplicationState(ApplicationState.LOADED)
            }

            override fun onLoadingSuccess() {
                receiverContentResolver.publishApplicationState(ApplicationState.LOADED)
            }
        })
    }

    private fun selectSource(position: Int) {
        sources.getOrNull(position)?.let { (_, path) ->
            receiverContentResolver.openRoute(path)
        }
    }

    private fun selectService(service: AVService) {
        receiverContentResolver.selectService(service)
        binding.serviceGuideView.setSelectedService(service.globalId)
    }

    private fun trySelectDefaultService() {
        if (stateLiveData.value?.state == ReceiverState.State.READY) {
            val selectedServiceId = binding.serviceGuideView.getSelectedServiceGlobalId()
            if (selectedServiceId == null) {
                serviceGuideManager.getDefaultService()?.let { service ->
                    selectService(service)
                }
            }
        }
    }

    private fun switchApplication(appData: AppData?) {
        with (binding.baView) {
            if (appData != null && appData.isAvailable) {
                if (!isInPictureInPictureMode) {
                    setBAAvailability(true)
                }

                if (!appData.isAppEquals(currentAppData) || (!isContentLoaded &&
                            (appData.isBCastAvailable != currentAppData?.isBCastAvailable
                                    || appData.isBBandAvailable != currentAppData?.isBBandAvailable))
                ) {
                    currentAppData = appData
                    serverCertificateHash = receiverContentResolver.queryServerCertificate() ?: emptyList()

                    loadFirstAvailable(
                        mutableListOf<String?>().apply {
                            if (appData.isBCastAvailable) {
                                add(appData.bCastEntryPageUrlFull)
                            }
                            if (appData.isBBandAvailable) {
                                add(appData.bBandEntryPageUrl)
                            }
                        }.filterNotNull()
                    )
                }
            } else {
                currentAppData = null
                unload()
                receiverContentResolver.publishApplicationState(ApplicationState.UNAVAILABLE)
            }
        }
    }

    private fun updateRMPLayout(x: Int, y: Int, scale: Double) {
        updateRMPLayout(x.toFloat() / 100, y.toFloat() / 100, scale.toFloat() / 100)
    }

    private fun updateRMPLayout(x: Float, y: Float, scale: Float) {
        ConstraintSet().apply {
            clone(binding.rmpPlayerContainer)
            setHorizontalBias(R.id.receiver_media_player, if (scale == 1f) 0f else x / (1f - scale))
            setVerticalBias(R.id.receiver_media_player, if (scale == 1f) 0f else y / (1f - scale))
            constrainPercentHeight(R.id.receiver_media_player, scale)
            constrainPercentWidth(R.id.receiver_media_player, scale)
        }.applyTo(binding.rmpPlayerContainer)
    }

    private fun startPlayback(mpdPath: Uri) {
        binding.receiverMediaPlayer.play(mpdPath)
        progressUpdateTimer.stop()
        binding.loadingProgress.visibility = View.GONE
    }

    private fun stopPlayback() {
        binding.receiverMediaPlayer.stopAndClear()
        binding.loadingProgress.visibility = View.VISIBLE
        progressUpdateTimer.start()
    }

    private fun checkForAppUpdates() {
        // Creates instance of the manager, returns an intent object that you use to check for an update.
        val appUpdateInfoTask: Task<AppUpdateInfo> = appUpdateManager.appUpdateInfo as Task<AppUpdateInfo>

        // Checks that the platform will allow the specified type of update. // For a flexible update, use AppUpdateType.FLEXIBLE
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            Log.i(TAG, "appUpdateInfoTask.onSuccessListener: appUpdateInfo: $appUpdateInfo")
            //jjustman-2021-05-04 - always push our ATSC3 sample app updates, e.g. dont gate on only && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                // Request the update.
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, APP_UPDATE_REQUEST_CODE)
            }
        }

        appUpdateInfoTask.addOnFailureListener { failedInfo ->
            Log.w(TAG, "appUpdateInfoTask.onFailureListener: failedInfo: "+failedInfo);
        }

    }

    private fun runScanningTimer(seconds: Long) {
        stopScanningTimer()

        scanningTime.value = seconds

        countDownTimer = object : CountDownTimer(seconds, TimeUnit.SECONDS.toMillis(1)) {
            override fun onTick(millisUntilFinished: Long) {
                scanningTime.value = millisUntilFinished
            }

            override fun onFinish() {}
        }.also {
            it.start()
        }
    }

    private fun stopScanningTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun formatSecondsToString(seconds: Long): String {
        return if (seconds >= 10) seconds.toString() else "0$seconds"
    }

    private fun checkIsPartnerService(service: AVService) {
        service.globalId?.let { serviceGlobalId ->
            val appPackage = getApkBaseServicePackage(service.category, serviceGlobalId)
            if (appPackage != null) {
                val started = packageManager.getLaunchIntentForPackage(appPackage)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(intent)
                        true
                    } catch (e: ActivityNotFoundException) {
                        false
                    }
                } ?: false

                if (!started) {
                    Toast.makeText(this@MainActivity, getString(R.string.message_service_apk_not_found, appPackage), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tryOpenPcapFile(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    ReceiverContentResolver.openRoute(applicationContext, uri)
                }
            }
        }
    }

    private val hideMediaControlsRunnable = Runnable {
        hideMediaControls()
    }

    private val hideAntennaIconRunnable = Runnable {
        binding.antennaIndicator.visibility = View.INVISIBLE
    }

    private val globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        // correct focuse for D-pad navigation
        with(binding) {
            if (newFocus == null) {
                root.requestFocusFromTouch()
            } else {
                when (motionLayout.state) {
                    ReceiverMotionLayout.IN_MIDDLE,
                    ReceiverMotionLayout.IN_END -> {
                        if (serviceGuideGrid.findViewById<View>(newFocus.id) == null) {
                            serviceGuideGrid.requestFocus()
                        }
                    }
                    ReceiverMotionLayout.IN_TOP -> {
                        if (sourcesList.findViewById<View>(newFocus.id) == null) {
                            sourcesList.requestFocus()
                        }
                    }
                    else -> {
                        if (isBAVisible && newFocus != root) {
                            baView.requestFocus()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName

        private const val PROGRESS_UPDATE_DELAY = 500L
        private const val MEDIA_CONTROLS_ANIMATION_DURATION = 400L
        private const val MEDIA_CONTROLS_HIDE_DELAY = 2000L

        private const val APP_UPDATE_REQUEST_CODE = 31338
        private const val YELLOW_ICON_SHOW_TIME = 5000L
        private const val MAX_CHANNEL_SCANNING_TIME = 20000L

        const val ACTION_PKG = "${BuildConfig.APPLICATION_ID}.intent.action"
        const val ACTION_OPEN_TUNE_DIALOG = "$ACTION_PKG.OPEN_TUNE"
        const val ACTION_TUNE = "$ACTION_PKG.TUNE"
        const val ACTION_SCAN = "$ACTION_PKG.SCAN"

        const val EXTRA_EXTRA = "frequency"
        const val EXTRA_FREQUENCY = "frequency"
    }
}