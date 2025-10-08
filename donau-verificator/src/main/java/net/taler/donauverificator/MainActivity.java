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
import android.os.Bundle;
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

import net.taler.donauverificator.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final String DEBUG_DONATION_STATEMENT =
            "donau://example.com/megacharity/1234/2025/7560001010000/1234?total=EUR:15&sig=ED25519:H9PM3BW3P8MEKB34GZ0G1F7JSNVX7B8AHXRFFMS37QZM7TXZ5MWPXTEDZZGN1QRB1AFPKNCFXJB39NJHP3BAFGCZSCXHEYPHA1YJY28&pub=K641W1CZM7DRSV184M8CPM3Z8MZRBYYJMNYMJK70FTYJHBPX21J0";
    private int PERMISSIONS_REQUEST_CAMERA = 0;
    private ActivityMainBinding binding;
    private CodeScanner mCodeScanner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //Check if CAMERA permission is granted
        if (ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") == PERMISSION_GRANTED) {
        } else {
            askPermission();
        }
        CodeScannerView scannerView = binding.scannerView;
        mCodeScanner = new CodeScanner(this, scannerView);
        mCodeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(@NonNull final Result result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sendRequestDialog(result.getText());
                    }
                });
            }
        });
        //temporary for debugging (valid donation statement from sample QR code)
        sendRequestDialog(DEBUG_DONATION_STATEMENT);

        binding.settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        mCodeScanner.startPreview();
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
                        Intent intent = new Intent(getApplicationContext(), Results.class);
                        intent.putExtra("QR-String", qrstring);
                        startActivity(intent);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        mCodeScanner.releaseResources();
                        mCodeScanner.startPreview();
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
     * Ask user to get camera permissions
     * Handles permission lifecycle
     */
    private void askPermission() {

        //Checks if a RequestPermissionRationale should be shown
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {

            AlertDialog.Builder alert = new AlertDialog.Builder(getApplicationContext());
            alert.setTitle("Please grant camera permission");
            alert.setMessage("This APP needs the camera permission to scan QR codes.");
            alert.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    checkPermission("android.permission.CAMERA", PERMISSIONS_REQUEST_CAMERA);
                }
            });
            alert.setIcon(android.R.drawable.ic_dialog_alert);
            alert.show();
        } else {
            //A permission request should be performed
            checkPermission("android.permission.CAMERA", PERMISSIONS_REQUEST_CAMERA);
        }
    }

    private void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[] {permission}, requestCode);
        } else {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show();
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
