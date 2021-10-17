package com.nextgenbroadcast.mobile.tvandroid

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionResolver(
    private val activity: AppCompatActivity,
    onPermissionGranted: () -> Unit
) {
    private val prefs: Prefs by lazy {
        Prefs(activity.applicationContext)
    }
    private val notificationManager: NotificationManager by lazy {
        activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val permissionRequestLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { grantResults ->
            val requiredPermissions = mutableListOf<String>()

            grantResults.forEach { (permission, granted) ->
                if (!granted) requiredPermissions.add(permission)
            }

            requiredPermissions.removeAll(optionalPermissions - necessaryPermissions)

            if (requiredPermissions.isNotEmpty()) {
                requestPermissions(requiredPermissions)
            } else {
                checkDNDPolicyAccess()
                onPermissionGranted()
            }

        }

    private var receiver: BroadcastReceiver? = null

    fun checkSelfPermission(): Boolean {
        val needsPermission = (necessaryPermissions + optionalPermissions).filter { permission ->
            ContextCompat.checkSelfPermission(activity.applicationContext, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermission.isNotEmpty()) {
            requestPermissions(needsPermission)
            return false
        }

        checkDNDPolicyAccess()

        return true
    }

    private fun requestPermissions(needsPermission: List<String>) {
        permissionRequestLauncher.launch(needsPermission.toTypedArray())
    }

    private fun checkDNDPolicyAccess() {
        receiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // ignore
            }
            receiver = null
        }

        with(prefs) {
            if (!isNotificationPolicyAccessRequested
                || isNotificationPolicyAccessGranted && !notificationManager.isNotificationPolicyAccessGranted
            ) {
                isNotificationPolicyAccessRequested = true
                isNotificationPolicyAccessGranted = false

                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED) {
                            isNotificationPolicyAccessGranted = notificationManager.isNotificationPolicyAccessGranted
                        }
                    }
                }.also {
                    activity.registerReceiver(it, IntentFilter().apply {
                        addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
                    })
                }

                showInfoDialog(R.string.permission_request_info_title, R.string.permission_request_info_dnd) {
                    activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
        }
    }


    private fun showInfoDialog(@StringRes titleResId: Int, @StringRes messageResId: Int, block: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(titleResId))
            .setMessage(activity.getString(messageResId))
            .setCancelable(false)
            .setNeutralButton(activity.getString(R.string.ok)) { _, _ ->
                block()
            }
            .setNegativeButton(activity.getString(R.string.cancel)){ _, _ -> }
            .show()
    }

    companion object {

        private val necessaryPermissions = listOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        private val optionalPermissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}