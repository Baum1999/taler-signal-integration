/*
  This file is part of TALER
  Copyright (C) 2024 Taler Systems SA

  TALER is free software; you can redistribute it and/or modify it under the
  terms of the GNU General Public License as published by the Free Software
  Foundation; either version 3, or (at your option) any later version.

  TALER is distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
  A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with
  TALER; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
*/

package net.taler.donauverificator;

import static androidx.core.content.PermissionChecker.PERMISSION_GRANTED;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.google.zxing.Result;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import net.taler.donauverificator.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
//    private static final String DEBUG_DONATION_STATEMENT =
//            "donau://example.com/megacharity/1234/2025/7560001010000/1234?total=EUR:15&sig=ED25519:H9PM3BW3P8MEKB34GZ0G1F7JSNVX7B8AHXRFFMS37QZM7TXZ5MWPXTEDZZGN1QRB1AFPKNCFXJB39NJHP3BAFGCZSCXHEYPHA1YJY28&pub=K641W1CZM7DRSV184M8CPM3Z8MZRBYYJMNYMJK70FTYJHBPX21J0";
//    private static final String DEBUG_DONATION_STATEMENT =
//            "donau://donau.test.taler.net/2025/123%2F456%2F789/AWNFDRFT0WX45W4Y32A9DJA03S1EF66GFQZ9EV5EF9JTHWZ37WR0?total=TESTKUDOS:1&sig=ED25519:B14WGS43FFPEB8JMSR6W1H8M6KH9AV33JFH376R6PM2MNH4GR24FP1C93C4ZPDG21W5WY4SASZQ4CRS427F4WJZJFZMQ5Y4HZNXGY30";
    private static final int REQ_CAMERA_PERMISSION = 100;
    private static final int REQ_APP_SETTINGS = 101;
    private static final String PREFS_PERMISSIONS = "permissions_prefs";
    private static final String KEY_CAMERA_DENIALS = "camera_denials";
    private ActivityMainBinding binding;
    private CodeScanner mCodeScanner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Check if CAMERA permission is granted; otherwise start permission flow
        if (!hasCameraPermission()) {
            askForCameraPermission();
        }
        CodeScannerView scannerView = binding.scannerView;
        mCodeScanner = new CodeScanner(this, scannerView);
        mCodeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(@NonNull final Result result) {
                runOnUiThread(() -> handleScannedText(result.getText()));
            }
        });

        //temporary for debugging (valid donation statement from sample QR code)
        //sendRequestDialog(DEBUG_DONATION_STATEMENT);

        binding.settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasCameraPermission()) {
            mCodeScanner.startPreview();
        }
    }

    @Override
    public void onPause() {
        mCodeScanner.releaseResources();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
    }
    /**
     * Asks user if app should proceed
     * @param qrstring
     */
    private void sendRequestDialog(String qrstring) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        //Yes button clicked
                        startResults(qrstring);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        if (mCodeScanner != null) {
                            mCodeScanner.startPreview();
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + which);
                }
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("QR scanned: '" + qrstring + "'\nWould you like to proceed?").setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }

    /**
     * Handle scanned QR content: if it's a valid Donau URI, proceed directly to Results.
     * Otherwise, show a brief message and resume scanning.
     */
    private void handleScannedText(String qrString) {
        if (isValidDonauUri(qrString)) {
            boolean autoOpen = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(SettingsActivity.KEY_AUTO_OPEN_DONAU, true);
            if (autoOpen) {
                startResults(qrString);
            } else {
                sendRequestDialog(qrString);
            }
            return;
        }
        Toast.makeText(this, R.string.scan_not_donau_link, Toast.LENGTH_SHORT).show();
        if (mCodeScanner != null) {
            mCodeScanner.startPreview();
        }
    }

    private void startResults(String qrString) {
        Intent intent = new Intent(getApplicationContext(), Results.class);
        intent.putExtra("QR-String", qrString);
        startActivity(intent);
    }

    private boolean isValidDonauUri(String raw) {
        if (raw == null) return false;
        Uri uri;
        try {
            uri = Uri.parse(raw);
        } catch (Exception e) {
            return false;
        }
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        String lowered = scheme.toLowerCase();
        if (!("donau".equals(lowered) || "donau+http".equals(lowered))) {
            return false;
        }
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            return false;
        }
        java.util.List<String> segments = uri.getPathSegments();
        if (segments == null || segments.size() < 3) {
            return false;
        }
        String year = segments.get(segments.size() - 3);
        if (!isFourDigitYear(year)) {
            return false;
        }
        String taxId = segments.get(segments.size() - 2);
        String salt = segments.get(segments.size() - 1);
        if (taxId == null || taxId.isEmpty()) return false;
        if (salt == null || salt.trim().isEmpty()) return false;
        return true;
    }

    private boolean isFourDigitYear(String value) {
        if (value == null || value.length() != 4) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request camera permission with rationale and fallback to app settings after repeated denials.
     */
    private void askForCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            showPermissionRationaleDialog();
        } else {
            requestCameraPermission();
        }
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
    }

    private void showPermissionRationaleDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_camera_title)
                .setMessage(getString(R.string.permission_camera_message))
                .setPositiveButton(R.string.permission_continue, (d, w) -> requestCameraPermission())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showGoToSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_camera_denied_title)
                .setMessage(getString(R.string.permission_camera_denied_message))
                .setPositiveButton(R.string.permission_open_settings, (d, w) -> openAppSettings())
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void incrementCameraDenialCount() {
        SharedPreferences prefs = getSharedPreferences(PREFS_PERMISSIONS, MODE_PRIVATE);
        int count = prefs.getInt(KEY_CAMERA_DENIALS, 0);
        prefs.edit().putInt(KEY_CAMERA_DENIALS, count + 1).apply();
    }

    private int getCameraDenialCount() {
        SharedPreferences prefs = getSharedPreferences(PREFS_PERMISSIONS, MODE_PRIVATE);
        return prefs.getInt(KEY_CAMERA_DENIALS, 0);
    }

    private void resetCameraDenialCount() {
        SharedPreferences prefs = getSharedPreferences(PREFS_PERMISSIONS, MODE_PRIVATE);
        prefs.edit().remove(KEY_CAMERA_DENIALS).apply();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                resetCameraDenialCount();
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                if (mCodeScanner != null) {
                    mCodeScanner.startPreview();
                }
            } else {
                incrementCameraDenialCount();
                boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA);
                if (!showRationale || getCameraDenialCount() >= 2) {
                    // User has denied twice or selected "Don't ask again" — guide to Settings
                    showGoToSettingsDialog();
                } else {
                    // Show rationale again for clarity
                    showPermissionRationaleDialog();
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
