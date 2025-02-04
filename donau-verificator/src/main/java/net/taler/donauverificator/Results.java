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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TextView;


import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class Results extends AppCompatActivity {
    static {
        System.loadLibrary("verification");
    }

    // QR-string : YEAR/TOTALAMOUNT/TAXID/TAXIDSALT/ED25519SIGNATURE/PUBKEY
    // CrockfordBase32 encoded: SIGNATURE, PUBLICKEY
    // TODO: Salt and taxId should maybe also be encoded

    private final int NUMBER_OF_ARGUMENTS = 6;
    private String year;
    private String totalAmount;
    private String taxId;
    private String salt;
    private String eddsaSignature;
    private String publicKey;
    TextView sigStatusView;
    TextView yearView;
    TextView taxidView;
    TextView totalView;
    TableLayout tableLayout;

    public enum SignatureStatus {
        INVALID_SCHEME,
        INVALID_NUMBER_OF_ARGUMENTS,
        MALFORMED_ARGUMENT,
        SIGNATURE_INVALID,
        SIGNATURE_VALID;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_results);
        sigStatusView = findViewById(R.id.sigStatus);
        yearView = findViewById(R.id.year);
        taxidView = findViewById(R.id.taxid);
        totalView = findViewById(R.id.total);
        tableLayout = findViewById(R.id.tableLayout);
        tableLayout.setVisibility(View.INVISIBLE);

        Intent intent = getIntent();
        String scheme[];
        // handle URI scheme
        if (null != intent.getData()) {
            scheme = new String[2];
            Uri uri = intent.getData();
            List<String> pathSegments = uri.getPathSegments();
            StringBuilder fullPath = new StringBuilder();
            fullPath.append(uri.getHost());
            for (String segment : pathSegments) {
                fullPath.append("/").append(segment);
            }
            scheme[1] = fullPath.toString();
        // handle self scanned QR code
        } else {
            scheme = intent.getStringExtra("QR-String").split("//");
            if (scheme == null || scheme.length != 2 || !scheme[0].equals("donau:")) {
                statusHandling(SignatureStatus.INVALID_SCHEME);
                return;
            }
        }
        String[] parts = scheme[1].split("/");
        if (parts == null || parts.length != NUMBER_OF_ARGUMENTS) {
            statusHandling(SignatureStatus.INVALID_NUMBER_OF_ARGUMENTS);
            return;
        }

        try {
            year = parts[0];
            totalAmount = parts[1];
            taxId = parts[2];
            salt = parts[3];
            eddsaSignature = parts[4];
            publicKey = parts[5];
        } catch (Exception e) {
            statusHandling(SignatureStatus.MALFORMED_ARGUMENT);
            return;
        }

        try {
            checkSignature();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void checkSignature() throws Exception{

        int res = ed25519_verify(year, totalAmount, taxId,
                salt, eddsaSignature, publicKey);
        System.out.println("Result: " + res);
        if (res == 0) {
            statusHandling(SignatureStatus.SIGNATURE_VALID);
        } else {
            statusHandling(SignatureStatus.SIGNATURE_INVALID);
        }
    }

    private void statusHandling(SignatureStatus es) {
        View rootView = findViewById(R.id.root_view);
        switch (es) {
            case INVALID_SCHEME:
                sigStatusView.setText(R.string.invalid_scheme);
                rootView.setBackgroundResource(R.color.red);
                break;
            case INVALID_NUMBER_OF_ARGUMENTS:
                sigStatusView.setText(R.string.invalid_number_of_arguments);
                rootView.setBackgroundResource(R.color.red);
                break;
            case MALFORMED_ARGUMENT:
                sigStatusView.setText(R.string.malformed_argument);
                rootView.setBackgroundResource(R.color.red);
                break;
            case SIGNATURE_INVALID:
                sigStatusView.setText(R.string.invalid_signature);
                rootView.setBackgroundResource(R.color.red);
                break;
            case SIGNATURE_VALID:
                tableLayout.setVisibility(View.VISIBLE);
                sigStatusView.setText(R.string.valid_signature);
                yearView.setText(year);
                taxidView.setText(taxId);
                totalView.setText(totalAmount);
                rootView.setBackgroundResource(R.color.green);
                break;
        }
    }

    public native int ed25519_verify(String year, String totalAmount,
                                     String taxId, String salt,
                                     String eddsaSignature, String publicKey);
}

