/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
 *
 * GNU Taler is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3, or (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.common

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_INITIAL_INTENTS
import android.graphics.BitmapFactory.decodeByteArray
import android.content.Intent.EXTRA_STREAM
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.os.Build.VERSION.SDK_INT
import android.os.Looper
import android.text.format.DateUtils.DAY_IN_MILLIS
import android.text.format.DateUtils.FORMAT_ABBREV_ALL
import android.text.format.DateUtils.FORMAT_ABBREV_MONTH
import android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
import android.text.format.DateUtils.FORMAT_NO_YEAR
import android.text.format.DateUtils.FORMAT_SHOW_DATE
import android.text.format.DateUtils.FORMAT_SHOW_TIME
import android.text.format.DateUtils.FORMAT_SHOW_YEAR
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.text.format.DateUtils.formatDateTime
import android.text.format.DateUtils.getRelativeTimeSpanString
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.taler.lib.android.ErrorBottomSheet
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.view.isVisible
import androidx.core.net.toUri

fun View.fadeIn(endAction: () -> Unit = {}) {
    if (isVisible && alpha == 1f) return
    alpha = 0f
    visibility = VISIBLE
    animate().alpha(1f).withEndAction {
        if (context != null) endAction.invoke()
    }.start()
}

fun View.fadeOut(endAction: () -> Unit = {}) {
    if (visibility == INVISIBLE) return
    animate().alpha(0f).withEndAction {
        if (context == null) return@withEndAction
        visibility = INVISIBLE
        alpha = 1f
        endAction.invoke()
    }.start()
}

fun View.hideKeyboard() {
    getSystemService(context, InputMethodManager::class.java)
        ?.hideSoftInputFromWindow(windowToken, 0)
}

fun assertUiThread() {
    check(Looper.getMainLooper().thread == Thread.currentThread())
}

/**
 * Use this with 'when' expressions when you need it to handle all possibilities/branches.
 */
val <T> T.exhaustive: T
    get() = this

@RequiresPermission(ACCESS_NETWORK_STATE)
fun Context.isOnline(): Boolean {
    val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (SDK_INT < 29) {
        @Suppress("DEPRECATION")
        cm.activeNetworkInfo?.isConnected == true
    } else {
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        capabilities.hasCapability(NET_CAPABILITY_INTERNET)
    }
}

fun FragmentActivity.showError(mainText: String, detailText: String = "") = ErrorBottomSheet
    .newInstance(mainText, detailText)
    .show(supportFragmentManager, "ERROR_BOTTOM_SHEET")

fun FragmentActivity.showError(@StringRes mainId: Int, detailText: String = "") {
    showError(getString(mainId), detailText)
}

fun Fragment.showError(mainText: String, detailText: String = "") = ErrorBottomSheet
    .newInstance(mainText, detailText)
    .show(parentFragmentManager, "ERROR_BOTTOM_SHEET")

fun Fragment.showError(@StringRes mainId: Int, detailText: String = "") {
    showError(getString(mainId), detailText)
}

fun Context.startActivitySafe(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e("taler-kotlin-android", "Error starting $intent", e)
    }
}

fun Context.canAppHandleUri(uri: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = uri.toUri()
    }

    return packageManager.queryIntentActivities(intent, 0).any {
        it.activityInfo.packageName != packageName
    }
}

fun Context.openUri(uri: String, title: String, excludeOwn: Boolean = true) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = uri.toUri()
    }

    if (excludeOwn) {
        val possiblePackageNames = mutableListOf<String>()
        val possibleIntents = packageManager.queryIntentActivities(intent, 0).filter {
            it.activityInfo.packageName != packageName
        }.map {
            val possibleIntent = Intent(intent)
            possibleIntent.`package` = it.activityInfo.packageName
            possiblePackageNames.add(it.activityInfo.packageName)
            return@map possibleIntent
        }

        val defaultResolveInfo = packageManager.resolveActivity(intent, 0)
        if (defaultResolveInfo == null || possiblePackageNames.isEmpty()) return

        // If there is a default app to handle the intent (which is not the app), use it.
        if (possiblePackageNames.contains(defaultResolveInfo.activityInfo.packageName)) {
            startActivitySafe(intent)
        } else {
            val chooser = Intent.createChooser(possibleIntents[0], title)
            chooser.putExtra(EXTRA_INITIAL_INTENTS, possibleIntents.drop(1).toTypedArray())
            startActivitySafe(chooser)
        }
    } else {
        startActivitySafe(Intent.createChooser(intent, title))
    }
}

fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }

    startActivitySafe(Intent.createChooser(intent, null))
}

fun Fragment.navigate(directions: NavDirections) = findNavController().navigate(directions)

fun Long.toRelativeTime(context: Context): CharSequence {
    val now = System.currentTimeMillis()
    return if (now - this > DAY_IN_MILLIS * 2) {
        val flags = FORMAT_SHOW_TIME or FORMAT_SHOW_DATE or FORMAT_ABBREV_MONTH or FORMAT_NO_YEAR
        formatDateTime(context, this, flags)
    } else getRelativeTimeSpanString(this, now, MINUTE_IN_MILLIS, FORMAT_ABBREV_RELATIVE)
}

fun Long.toAbsoluteTime(context: Context): CharSequence {
    val flags = FORMAT_SHOW_TIME or FORMAT_SHOW_DATE or FORMAT_SHOW_YEAR
    return formatDateTime(context, this, flags)
}

fun Long.toShortDate(context: Context): CharSequence {
    val flags = FORMAT_SHOW_DATE or FORMAT_SHOW_YEAR or FORMAT_ABBREV_ALL
    return formatDateTime(context, this, flags)
}

fun Version.getIncompatibleStringOrNull(context: Context, otherVersion: String): String? {
    val other = Version.parse(otherVersion) ?: return context.getString(R.string.version_invalid)
    val match = compare(other) ?: return context.getString(R.string.version_invalid)
    if (match.compatible) return null
    if (match.currentCmp < 0) return context.getString(R.string.version_too_old)
    if (match.currentCmp > 0) return context.getString(R.string.version_too_new)
    throw AssertionError("$this == $other")
}

fun copyToClipBoard(context: Context, label: String, str: String) {
    val clipboard = context.getSystemService<ClipboardManager>()
    val clip = ClipData.newPlainText(label, str)
    clipboard?.setPrimaryClip(clip)
}

const val SHARE_QR_TEMP_PREFIX = "taler_qr_"
const val SHARE_QR_SIZE = 512
const val SHARE_QR_QUALITY = 90

/**
 * Share string as QR code via sharing dialog
 *
 * NOTE: make sure to properly setup file provider
 * https://developer.android.com/training/secure-file-sharing/setup-sharing
 */
suspend fun String.shareAsQrCode(context: Context, authority: String) {
    val qrBitmap = QrCodeManager.makeQrCode(this, SHARE_QR_SIZE)
    val outputDir = context.cacheDir
    try {
        val uri = withContext(Dispatchers.IO) {
            val outputFile = File.createTempFile(SHARE_QR_TEMP_PREFIX, ".png", outputDir)
            outputFile.deleteOnExit()
            val stream = FileOutputStream(outputFile)
            qrBitmap.compress(Bitmap.CompressFormat.PNG, SHARE_QR_QUALITY, stream)
            stream.flush()
            stream.close()
            FileProvider.getUriForFile(context, authority, outputFile)
        }

        // TODO: also allow saving QR to files (under a human-readable name?)
        val intent = Intent(ACTION_SEND).apply {
            putExtra(EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            setType("image/png")
        }

        val shareIntent = Intent.createChooser(intent, null)
        context.startActivitySafe(shareIntent)
    } catch(e: IOException) {
        Log.d("taler-kotlin-android", "Failed to generate or store PNG image")
    }
}

private val REGEX_BASE64_IMAGE = Regex("^data:image/(jpeg|png);base64,([A-Za-z0-9+/=]+)$")

val String.base64Bitmap: Bitmap?
    get() = REGEX_BASE64_IMAGE.matchEntire(this)?.let { match ->
        match.groups[2]?.value?.let { group ->
            val decodedString = Base64.decode(group, Base64.DEFAULT)
            decodeByteArray(decodedString, 0, decodedString.size)
        }
    }