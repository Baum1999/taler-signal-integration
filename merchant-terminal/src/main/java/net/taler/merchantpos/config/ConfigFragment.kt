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

package net.taler.merchantpos.config

import android.Manifest
import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.taler.common.navigate
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.config.ConfigFragmentDirections.Companion.actionSettingsToOrder
import net.taler.merchantpos.databinding.FragmentMerchantConfigBinding
import androidx.core.view.isVisible
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import net.taler.merchantpos.MainActivity
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Fragment that displays merchant settings, either by scanning a QR code
 * or by manual token entry.
 */
class ConfigFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val configManager by lazy { model.configManager }

    private lateinit var ui: FragmentMerchantConfigBinding

    private val scanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    private val cameraExecutor by lazy {
        ContextCompat.getMainExecutor(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ui = FragmentMerchantConfigBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 1) Views
        val neverOption       = ui.root.findViewById<RadioButton>(R.id.neverExpiresOption)
        val dateOption        = ui.root.findViewById<RadioButton>(R.id.dateExpiresOption)
        val deadlineLayout    = ui.root.findViewById<View>(R.id.deadlinePickerLayout)
        val selectDateButton  = ui.root.findViewById<Button>(R.id.selectDateButton)
        val selectTimeButton  = ui.root.findViewById<Button>(R.id.selectTimeButton)
        val selectedDeadline  = ui.root.findViewById<TextView>(R.id.selectedDeadline)

        // 2) Shared Calendar instance for storing the deadline
        val deadlineCal = Calendar.getInstance()

        // set initial toggle
        ui.configToggle.check(R.id.newConfigButton)

        // wire up toggle group for QR vs manual
        ui.configToggle.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean ->
            if (!isChecked) return@addOnButtonCheckedListener

            when (checkedId) {
                R.id.qrConfigButton -> showQrConfig()
                R.id.newConfigButton -> showManualConfig()
            }
        }

        // 1) Extract base URL and username if pasted with /instances/username
        // Only parse URL when user finishes editing (focus lost)
        ui.merchantUrlView.editText!!.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                parseMerchantUrlAndUpdateFields()
            }
        }

        // manual configuration OK button
        ui.okNewButton.setOnClickListener {
            // launch coroutine to fetch limited token before config update
            lifecycleScope.launch {
                // prepare UI
                ui.progressBarNew.visibility = VISIBLE
                ui.okNewButton.visibility = INVISIBLE

                // normalize URL
                val inputUrl = ui.merchantUrlView.editText!!.text.toString()
                val url = if (inputUrl.startsWith("http")) inputUrl else "https://$inputUrl"

                // retrieve username (may have been set by listener)
                val username = ui.usernameView.editText!!.text.toString().trim()
                // initial secret/token from user
                val initialSecret = ui.tokenView.editText!!.text.toString().trim()

                val duration: TokenDuration = if (neverOption.isChecked) {
                    TokenDuration.Forever
                } else {
                    val microsToDeadline = (deadlineCal.timeInMillis - System.currentTimeMillis()) * 1_000L
                    TokenDuration.Micros(microsToDeadline)
                }

                // fetch limited write token
                val limitedToken = try {
                    withContext(Dispatchers.IO) {
                        configManager.fetchLimitedAccessToken(url, username, initialSecret, duration)
                    }
                } catch (e: Exception) {
                    ui.progressBarNew.visibility = INVISIBLE
                    ui.okNewButton.visibility = VISIBLE
                    Log.e("ConfigFragment", "Error fetching limited token: ${e.message}")
                    Snackbar.make(requireView(), getString(R.string.config_error_network), LENGTH_LONG).show()
                    return@launch
                }

                val configUrl = "$url/instances/$username"

                // proceed with normal config fetch using limited token
                val config = Config.New(
                    merchantUrl = configUrl,
                    accessToken = limitedToken,
                    savePassword = ui.saveTokenCheckBox.isChecked
                )
                configManager.fetchConfig(config, true)
                configManager.configUpdateResult.observe(viewLifecycleOwner) { result ->
                    if (onConfigUpdate(result)) {
                        configManager.configUpdateResult.removeObservers(viewLifecycleOwner)
                    }
                }
            }
        }


        fun updateDeadlineText() {
            val fmt = java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm", Locale.getDefault())
            selectedDeadline.text = fmt.format(deadlineCal.time)
        }


        ui.expiryOptionGroup.setOnCheckedChangeListener { _, checkedId ->
            deadlineLayout.visibility = if (checkedId == R.id.dateExpiresOption) VISIBLE else GONE
        }

        selectDateButton.setOnClickListener {
            val year  = deadlineCal.get(Calendar.YEAR)
            val month = deadlineCal.get(Calendar.MONTH)
            val day   = deadlineCal.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(requireContext(), { _, y, m, d ->
                deadlineCal.set(y, m, d)
                updateDeadlineText()
            }, year, month, day).show()
        }

        selectTimeButton.setOnClickListener {
            val hour   = deadlineCal.get(Calendar.HOUR_OF_DAY)
            val minute = deadlineCal.get(Calendar.MINUTE)
            TimePickerDialog(requireContext(), { _, h, min ->
                deadlineCal.set(Calendar.HOUR_OF_DAY, h)
                deadlineCal.set(Calendar.MINUTE, min)
                updateDeadlineText()
            }, hour, minute, DateFormat.is24HourFormat(requireContext())).show()
        }

        updateView(savedInstanceState == null)
    }

    override fun onStart() {
        super.onStart()

    }

    override fun onResume() {
        super.onResume()
        // if QR form is showing, re-request camera
        if (ui.qrConfigForm.isVisible) {
            requestCameraIfNeeded()
        }
    }

    override fun onDestroyView() {
        // ensure camera is released
        stopCamera()
        super.onDestroyView()
    }

    private fun showQrConfig() {
        Log.d("ConfigFragment", "showQrConfig() → requesting camera")
        ui.qrConfigForm.visibility = VISIBLE
        ui.newConfigForm.visibility = GONE
        requestCameraIfNeeded()
    }

    private fun showManualConfig() {
        ui.qrConfigForm.visibility = GONE
        ui.newConfigForm.visibility = VISIBLE
        stopCamera()
    }

    private fun updateView(isInitialization: Boolean = false) {
        if (isInitialization) {
            ui.merchantUrlView.editText!!.setText(NEW_CONFIG_URL_DEMO)

            when (val cfg = configManager.config) {
                is Config.New -> {
                    if (cfg.merchantUrl.isNotBlank()) {
                        ui.merchantUrlView.editText!!.setText(cfg.merchantUrl)
                        parseMerchantUrlAndUpdateFields()
                    }
                    ui.saveTokenCheckBox.isChecked = cfg.savePassword
                }
            }
        }

        when (configManager.config) {
            is Config.New -> {
                ui.configToggle.check(R.id.newConfigButton)
                showManualConfig()
            }
        }
    }

    private fun onConfigUpdate(result: ConfigUpdateResult?) = when (result) {
        null -> false
        is ConfigUpdateResult.Error -> {
            onError(result.msg)
            true
        }
        is ConfigUpdateResult.Success -> {
            onConfigReceived(result.currency)
            true
        }
    }

    private fun onConfigReceived(currency: String) {
        onResultReceived()
        updateView()
        Snackbar.make(requireView(), getString(R.string.config_changed, currency), LENGTH_LONG).show()
        navigate(actionSettingsToOrder())
    }

    private fun onError(msg: String) {
        onResultReceived()
        Snackbar.make(requireView(), msg, LENGTH_LONG).show()
    }

    private fun onResultReceived() {
        ui.progressBarNew.visibility = INVISIBLE
        ui.okNewButton.visibility = VISIBLE
    }

    private fun parseMerchantUrlAndUpdateFields() {
        val input =  ui.merchantUrlView.editText!!.text.toString().trim()
        val uri = input.toUri()
        // Build base URL: scheme://host[:port]
        val scheme = uri.scheme ?: ""
        val host = uri.host ?: ""
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val baseUrl = "$scheme://$host$port"
        // Check for /instances/username
        val segments = uri.pathSegments
        if (segments.size >= 2 && segments[0].equals("instances", true)) {
            //Ensure that the username has been transferred to the username field
            ui.usernameView.editText!!.setText(segments[1])
        }
        // Ensure merchant URL has only the base
        ui.merchantUrlView.editText!!.setText(baseUrl)
    }


    // ─── CameraX integration ───────────────────────────────────────────

    // 1) permission launcher
    private val requestCameraPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("ConfigFragment", "CAMERA permission granted? $granted")
            if (granted) startCamera()
            else Toast.makeText(requireContext(),
                R.string.config_fragment_camera_needed_text, Toast.LENGTH_SHORT).show()
        }

    // 2) request if needed
    private fun requestCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPerm.launch(Manifest.permission.CAMERA)
        }
    }

    // 3) start CameraX preview
    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        Log.d("ConfigFragment", "startCamera() called")
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(ui.previewView.surfaceProvider)
            }
            
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { useCase ->
                    useCase.setAnalyzer(cameraExecutor) { proxy ->
                        val media = proxy.image ?: run { proxy.close(); return@setAnalyzer }
                        val image = InputImage.fromMediaImage(
                                                media,
                                                proxy.imageInfo.rotationDegrees
                                                )
                        scanner.process(image)
                            .addOnSuccessListener { codes ->
                                codes.firstOrNull()?.rawValue?.let { onQrDecoded(it) }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                }
            
            provider.unbindAll()
            provider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }, cameraExecutor)
    }

     private fun onQrDecoded(raw: String) {
         if (!raw.startsWith("taler-pos://")) return          // guard
        
         stopCamera()                                         // freeze picture
         // Re-use the rock-solid parsing inside MainActivity
         val intent = Intent(Intent.ACTION_VIEW, raw.toUri())
            (requireActivity() as MainActivity).handleSetupIntent(intent)
        
         // show loader until ConfigFetcherFragment takes over
         ui.progressBarQr.visibility = VISIBLE
         ui.previewView.visibility = View.INVISIBLE
     }
    
    // 4) release camera
    private fun stopCamera() {
        try {
            ProcessCameraProvider.getInstance(requireContext())
                .get()
                .unbindAll()
        } catch (_: Exception) { /* no-op */ }
    }
}

