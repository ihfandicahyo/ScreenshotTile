package com.github.cvzi.screenshottile.services

import android.app.Activity
import android.content.Context
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.github.cvzi.screenshottile.App
import com.github.cvzi.screenshottile.BuildConfig
import com.github.cvzi.screenshottile.R
import com.github.cvzi.screenshottile.activities.NoDisplayActivity
import com.github.cvzi.screenshottile.activities.NoDisplayActivity.Companion.EXTRA_HIDE_QUICK_SETTINGS_PANEL
import com.github.cvzi.screenshottile.services.ScreenshotAccessibilityService.Companion.openAccessibilitySettings
import com.github.cvzi.screenshottile.utils.getLocalizedString
import com.github.cvzi.screenshottile.utils.isDeviceLocked
import com.github.cvzi.screenshottile.utils.setUserLanguage
import com.github.cvzi.screenshottile.utils.startActivityAndCollapseCustom


/**
 * Quick settings tile to switch the floating button on/off
 *
 * Created by cuzi (cuzi@openmail.cc).
 */

@RequiresApi(Build.VERSION_CODES.P)
class FloatingTileService : TileService() {
    companion object {
        private const val TAG = "FloatingTileService"
        var instance: FloatingTileService? = null
        var informAccessibilityServiceOnLocked = false

        fun toggleFloatingButton(context: Context) {
            val prefManager = App.getInstance().prefManager
            if (ScreenshotAccessibilityService.instance == null) {
                // Always enable if accessibility service is not yet running
                prefManager.floatingButton = true
                if (context is Activity) {
                    openAccessibilitySettings(context)
                } else if (context is TileService) {
                    openAccessibilitySettings(context)
                }
            } else {
                if (prefManager.floatingButton && prefManager.packageNameFilterEnabled && ScreenshotAccessibilityService.instance?.floatingButtonShown == false) {
                    // Floating button is currently hidden by package filter
                    // -> Temporary show it
                    ScreenshotAccessibilityService.instance?.overridePackageFilterTempShow()
                } else if (prefManager.floatingButton && prefManager.packageNameFilterEnabled && ScreenshotAccessibilityService.instance?.floatingButtonShown == true) {
                    // Floating button is currently shown through package filter
                    // -> Temporary hide it
                    ScreenshotAccessibilityService.instance?.overridePackageFilterTempHide()
                } else {
                    // Toggle
                    prefManager.floatingButton =
                        !prefManager.floatingButton
                    ScreenshotAccessibilityService.instance?.updateFloatingButton()
                }

                // Close quick settings panel
                val intent = NoDisplayActivity.newIntent(context, false).apply {
                    action = EXTRA_HIDE_QUICK_SETTINGS_PANEL
                    flags = FLAG_ACTIVITY_NEW_TASK
                }
                if (context is TileService) {
                    context.startActivityAndCollapseCustom(intent)
                } else {
                    context.startActivity(intent)
                }
            }
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        instance = this
        setUserLanguage()
    }

    private fun setState(newState: Int) {
        try {
            qsTile?.run {
                state = newState
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    qsTile.label = getLocalizedString(R.string.tile_floating_label)
                    qsTile.subtitle = getLocalizedString(R.string.tile_floating_subtitle)
                }
                updateTile()
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "setState: IllegalStateException", e)
        } catch (e: NullPointerException) {
            Log.e(TAG, "setState: NullPointerException", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "setState: IllegalArgumentException", e)
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        if (BuildConfig.DEBUG) Log.v(TAG, "onTileAdded()")
        App.checkAccessibilityServiceOnCollapse(true)
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        if (BuildConfig.DEBUG) Log.v(TAG, "onStartListening()")
        updateTileState()
        if (informAccessibilityServiceOnLocked && isDeviceLocked(this)) {
            ScreenshotAccessibilityService.instance?.onDeviceLockedFromTileService()
        }
        if (ScreenshotTileService.instance == null && ScreenshotAccessibilityService.instance != null && !App.getInstance().prefManager.floatingButtonWhenQuickSettings) {
            ScreenshotAccessibilityService.instance?.hideFloatingButton()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        if (BuildConfig.DEBUG) Log.v(TAG, "onStopListening()")
        if (App.checkAccessibilityServiceOnCollapse()) {
            App.checkAccessibilityServiceOnCollapse(false)
            Handler(Looper.getMainLooper()).postDelayed({
                if (App.getInstance().prefManager.useNative && ScreenshotAccessibilityService.instance == null) {
                    openAccessibilitySettings(this)
                }
            }, 5000)
        }
        if (ScreenshotTileService.instance == null && ScreenshotAccessibilityService.instance != null && !App.getInstance().prefManager.floatingButtonWhenQuickSettings) {
            ScreenshotAccessibilityService.instance?.updateFloatingButton(animate = false)
        }
    }

    override fun onClick() {
        super.onClick()
        if (BuildConfig.DEBUG) Log.v(TAG, "onClick()")
        toggleFloatingButton(this)
        updateTileState()
    }

    private fun updateTileState() {
        // Set tile state according to settings and check if accessibility service is running
        setState(
            if (App.getInstance().prefManager.floatingButton && ScreenshotAccessibilityService.instance != null) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
        )
    }

    override fun onDestroy() {
        instance = null
    }

}
