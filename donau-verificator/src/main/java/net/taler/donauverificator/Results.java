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
import java.util.Locale;

public class Results extends AppCompatActivity {
    static {
        System.loadLibrary("verification");
    }

    // lsd0013 format: donau://host/year/taxid/salt?total=...&sig=ED25519:...
    // CrockfordBase32 encoded: SIGNATURE, PUBLICKEY
    // TODO: Salt and taxId should maybe also be encoded

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
        Uri uri = resolveUri(intent);
        if (uri == null) {
            statusHandling(SignatureStatus.INVALID_SCHEME);
            return;
        }

        String scheme = uri.getScheme();
        if (!isSupportedScheme(scheme)) {
            statusHandling(SignatureStatus.INVALID_SCHEME);
            return;
        }

        resetParsedFields();
        SignatureStatus parseStatus = parseDonauUri(uri);
        if (parseStatus != null) {
            statusHandling(parseStatus);
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

    private Uri resolveUri(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            return data;
        }
        String raw = intent.getStringExtra("QR-String");
        if (raw == null) {
            return null;
        }
        return Uri.parse(raw);
    }

    private boolean isSupportedScheme(String scheme) {
        if (scheme == null) {
            return false;
        }
        String lowered = scheme.toLowerCase(Locale.ROOT);
        return "donau".equals(lowered) || "donau+http".equals(lowered);
    }

    private void resetParsedFields() {
        year = null;
        totalAmount = null;
        taxId = null;
        salt = null;
        eddsaSignature = null;
        publicKey = null;
    }

    private SignatureStatus parseDonauUri(Uri uri) {
        String host = uri.getHost();
        if (isEmpty(host)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }

        List<String> segments = uri.getPathSegments();
        if (segments == null) {
            return SignatureStatus.INVALID_NUMBER_OF_ARGUMENTS;
        }

        if (segments.size() < 3) {
            return SignatureStatus.INVALID_NUMBER_OF_ARGUMENTS;
        }

        int lastIndex = segments.size() - 1;
        String saltCandidate = segments.get(lastIndex);
        String taxIdCandidate = segments.get(lastIndex - 1);
        String yearCandidate = segments.get(lastIndex - 2);

        if (yearCandidate != null) {
            yearCandidate = yearCandidate.trim();
        }
        if (!isFourDigitYear(yearCandidate)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }

        year = yearCandidate;

        if (taxIdCandidate != null) {
            taxIdCandidate = taxIdCandidate.trim();
        }
        if (!isValidTaxId(taxIdCandidate)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }
        taxId = taxIdCandidate;

        if (saltCandidate != null) {
            saltCandidate = saltCandidate.trim();
        }
        if (!isDigitsOnly(saltCandidate)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }
        salt = saltCandidate;

        String totalParam = uri.getQueryParameter("total");
        if (isEmpty(totalParam)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }
        totalAmount = totalParam.trim();

        String sigParam = uri.getQueryParameter("sig");
        if (isEmpty(sigParam)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }
        eddsaSignature = extractEd25519Signature(sigParam);
        if (isEmpty(eddsaSignature)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }

        //TODO: Remove to follow the lsd0013
        // we can do it, when we have a donau instance in open web
        String publicKeyParam = uri.getQueryParameter("pub");
        if (isEmpty(publicKeyParam)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }
        publicKey = publicKeyParam.trim();

        return null;
    }

    private boolean isFourDigitYear(String value) {
        if (value == null || value.length() != 4) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String extractEd25519Signature(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        int separatorIndex = trimmed.indexOf(':');
        if (separatorIndex < 0) {
            separatorIndex = trimmed.indexOf('=');
        }
        if (separatorIndex <= 0 || separatorIndex >= trimmed.length() - 1) {
            return null;
        }
        String algorithm = trimmed.substring(0, separatorIndex).trim();
        if (!"ED25519".equalsIgnoreCase(algorithm)) {
            return null;
        }
        String signature = trimmed.substring(separatorIndex + 1).trim();
        if (signature.isEmpty()) {
            return null;
        }
        return signature;
    }

    private boolean isDigitsOnly(String value) {
        if (isEmpty(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidTaxId(String value) {
        if (isEmpty(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '-' || ch == '.')) {
                return false;
            }
        }
        return true;
    }
}
