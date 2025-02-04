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
        //temporary for debugging
        sendRequestDialog("donau://2024/EUR:15/7560001010000/1234/SAAM5BA1F9H4VT6T78CFC3X63HAMY2TXB597XBVZ0EMXEZ90QPJ3000BXDBJ3ECHGB8AEX9FFQ5BAXVSF6X6NXM98PY353F2R99PP1R/E24CDJHGSPZG20ZSSTMTBREGCCP495WKETQYCYA9C93EPMZN4FEG");

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

}