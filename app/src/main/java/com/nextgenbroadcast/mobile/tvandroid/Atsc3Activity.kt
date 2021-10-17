package com.nextgenbroadcast.mobile.tvandroid

import android.content.*
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.nextgenbroadcast.mobile.core.dev.service.binder.IServiceBinder
import com.nextgenbroadcast.mobile.middleware.service.Atsc3ForegroundService
import com.nextgenbroadcast.mobile.middleware.service.EmbeddedAtsc3Service
import java.util.concurrent.TimeUnit

abstract class Atsc3Activity : AppCompatActivity() {

    private val permissionResolver: PermissionResolver by lazy {
        PermissionResolver(this, ::onPermissionGranted)
    }

    private lateinit var locationLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var mediaBrowser: MediaBrowserCompat

    var isBound: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, EmbeddedAtsc3Service::class.java),
            connectionCallbacks,
            null
        )
    }

    private fun onPermissionGranted() {
        checkLocationProviderEnabled()
        bindService()
    }

    override fun onStart() {
        super.onStart()

        if (permissionResolver.checkSelfPermission()) {
            checkLocationProviderEnabled()
            bindService()
        }

        mediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()

        unbindService()

        mediaBrowser.disconnect()
    }

    private fun bindService() {
        if (isBound) return

        Intent(this, EmbeddedAtsc3Service::class.java).apply {
            action = EmbeddedAtsc3Service.SERVICE_INTERFACE
            putExtra(Atsc3ForegroundService.EXTRA_PLAY_AUDIO_ON_BOARD, true)
        }.also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    private fun unbindService() {
        if (!isBound) return

        unbindService(connection)
        isBound = false
        onUnbind()
    }

    abstract fun onBind(binder: IServiceBinder)
    abstract fun onUnbind()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as? IServiceBinder ?: run {
                Toast.makeText(this@Atsc3Activity, R.string.service_action_disconnect, Toast.LENGTH_LONG).show()
                return
            }

            onBind(binder)
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    open fun onSourcesAvailable(sources: List<MediaBrowserCompat.MediaItem>) {}
    open fun onMediaSessionCreated() {}

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val mediaController = MediaControllerCompat(this@Atsc3Activity, mediaBrowser.sessionToken)
            MediaControllerCompat.setMediaController(this@Atsc3Activity, mediaController)

            mediaBrowser.subscribe(mediaBrowser.root, object : MediaBrowserCompat.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
                    onSourcesAvailable(children)
                }
            })

            onMediaSessionCreated()
        }
    }

    private fun checkLocationProviderEnabled() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isLocationEnabled) {
            showEnableLocationDialog()
        }
    }

    private fun showEnableLocationDialog() {
        val settingsBuilder = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setInterval(TimeUnit.MINUTES.toMillis(30))
                .setFastestInterval(TimeUnit.MINUTES.toMillis(1))
            )
            .setAlwaysShow(true)
            .build()
        LocationServices.getSettingsClient(this)
            .checkLocationSettings(settingsBuilder)
            .addOnCompleteListener { task ->
                try {
                    task.getResult(ApiException::class.java)
                } catch (apiException: ApiException) {
                    if (apiException.statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                        (apiException as? ResolvableApiException)?.let { resolvableException ->
                            requestEnableLocation(resolvableException)
                        }
                    }
                }
            }
    }

    private fun requestEnableLocation(resolvableException: ResolvableApiException) {
        try {
            locationLauncher.launch(IntentSenderRequest.Builder(resolvableException.resolution).build())
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Can't enable location service", e)
        }
    }

    companion object {
        val TAG: String = Atsc3Activity::class.java.simpleName
    }
}