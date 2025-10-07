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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class Results extends AppCompatActivity {
    static {
        System.loadLibrary("verification");
    }

    private static final String TAG = "Results";

    // lsd0013 format: donau://host/year/taxid/salt?total=...&sig=ED25519:...
    // CrockfordBase32 encoded: SIGNATURE, PUBLICKEY
    // TODO: Salt and taxId should maybe also be encoded

    private String uriScheme;
    private String host;
    private int port = -1;
    private final List<String> authorityPathSegments = new ArrayList<>();
    private String year;
    private String totalAmount;
    private String taxId;
    private String hostDisplay;
    private String salt;
    private String eddsaSignature;
    private String publicKey;
    TextView sigStatusView;
    View summaryContainer;
    TextView hostLabelView;
    TextView hostValueView;
    TextView yearLabelView;
    TextView yearValueView;
    TextView taxLabelView;
    TextView taxValueView;
    TextView amountLabelView;
    TextView amountValueView;
    MaterialCardView statusCard;
    MaterialButton signatureButton;

    public enum SignatureStatus {
        INVALID_SCHEME,
        INVALID_NUMBER_OF_ARGUMENTS,
        MALFORMED_ARGUMENT,
        INSECURE_HTTP_UNSUPPORTED,
        INSECURE_HTTP_DISABLED,
        KEY_DOWNLOAD_FAILED,
        KEY_NOT_FOUND,
        SIGNATURE_INVALID,
        SIGNATURE_VALID;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_results);
        sigStatusView = findViewById(R.id.sigStatus);
        statusCard = findViewById(R.id.statusCard);
        summaryContainer = findViewById(R.id.summaryContainer);
        hostLabelView = findViewById(R.id.hostLabel);
        hostValueView = findViewById(R.id.hostValue);
        yearLabelView = findViewById(R.id.yearLabel);
        yearValueView = findViewById(R.id.yearValue);
        taxLabelView = findViewById(R.id.taxLabel);
        taxValueView = findViewById(R.id.taxValue);
        amountLabelView = findViewById(R.id.amountLabel);
        amountValueView = findViewById(R.id.amountValue);
        signatureButton = findViewById(R.id.signatureButton);
        if (signatureButton != null) {
            signatureButton.setVisibility(View.GONE);
            signatureButton.setEnabled(false);
            signatureButton.setOnClickListener(v -> showSignatureDialog());
        }
        if (statusCard != null) {
            statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.validation_surface_neutral));
        }
        sigStatusView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        setSummaryLabels();
        setupBackNavigation();

        Intent intent = getIntent();
        Uri uri = resolveUri(intent);
        if (uri == null) {
            statusHandling(SignatureStatus.INVALID_SCHEME);
            return;
        }

        uriScheme = uri.getScheme();
        if (!isSupportedScheme(uriScheme)) {
            statusHandling(SignatureStatus.INVALID_SCHEME);
            return;
        }

        resetParsedFields();
        SignatureStatus parseStatus = parseDonauUri(uri);
        if (parseStatus != null) {
            statusHandling(parseStatus);
            return;
        }
        startVerificationAsync();
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

    private void startVerificationAsync() {
        new Thread(() -> {
            SignatureStatus status = ensurePublicKeyAvailable();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (status != null) {
                    statusHandling(status);
                    return;
                }
                try {
                    checkSignature();
                } catch (Exception e) {
                    Log.e(TAG, "Signature verification failed", e);
                    statusHandling(SignatureStatus.SIGNATURE_INVALID);
                }
            });
        }).start();
    }

    private void statusHandling(SignatureStatus es) {
        switch (es) {
            case INVALID_SCHEME:
                updateStatusCard(R.string.invalid_scheme, R.color.validation_surface_error, R.color.red, false);
                break;
            case INVALID_NUMBER_OF_ARGUMENTS:
                updateStatusCard(R.string.invalid_number_of_arguments, R.color.validation_surface_error, R.color.red, false);
                break;
            case MALFORMED_ARGUMENT:
                updateStatusCard(R.string.malformed_argument, R.color.validation_surface_error, R.color.red, false);
                break;
            case INSECURE_HTTP_UNSUPPORTED:
                updateStatusCard(R.string.status_insecure_http_unsupported, R.color.validation_surface_neutral, R.color.text_primary, false);
                break;
            case INSECURE_HTTP_DISABLED:
                updateStatusCard(R.string.status_insecure_http_disabled, R.color.validation_surface_neutral, R.color.text_primary, false);
                break;
            case KEY_DOWNLOAD_FAILED:
                updateStatusCard(R.string.status_key_download_failed, R.color.validation_surface_error, R.color.red, false);
                break;
            case KEY_NOT_FOUND:
                updateStatusCard(R.string.status_key_not_found, R.color.validation_surface_info, R.color.colorSecondary, false);
                break;
            case SIGNATURE_INVALID:
                updateStatusCard(R.string.invalid_signature, R.color.validation_surface_error, R.color.red, false);
                break;
            case SIGNATURE_VALID:
                updateStatusCard(R.string.valid_signature, R.color.validation_surface_success, R.color.green, true);
                setSummaryValues(year, taxId, totalAmount);
                break;
        }
    }

    private void updateStatusCard(@StringRes int messageRes,
                                  @ColorRes int backgroundColorRes,
                                  @ColorRes int textColorRes,
                                  boolean showDetails) {
        sigStatusView.setText(messageRes);
        if (statusCard != null) {
            statusCard.setCardBackgroundColor(ContextCompat.getColor(this, backgroundColorRes));
        }
        sigStatusView.setTextColor(ContextCompat.getColor(this, textColorRes));
        if (summaryContainer != null) {
            summaryContainer.setVisibility(showDetails ? View.VISIBLE : View.GONE);
        }
        if (signatureButton != null) {
            signatureButton.setVisibility(showDetails ? View.VISIBLE : View.GONE);
            signatureButton.setEnabled(showDetails);
        }
        if (showDetails) {
            setSummaryValues(year, taxId, totalAmount);
        } else {
            clearSummaryValues();
        }
    }

    private void setSummaryLabels() {
        if (hostLabelView != null) {
            hostLabelView.setText(R.string.label_host);
            hostLabelView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        if (yearLabelView != null) {
            yearLabelView.setText(R.string.label_year);
            yearLabelView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        if (taxLabelView != null) {
            taxLabelView.setText(R.string.label_tax_id);
            taxLabelView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        if (amountLabelView != null) {
            amountLabelView.setText(R.string.label_amount);
            amountLabelView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        clearSummaryValues();
    }

    private void clearSummaryValues() {
        if (hostValueView != null) {
            hostValueView.setText("");
        }
        if (yearValueView != null) {
            yearValueView.setText("");
        }
        if (taxValueView != null) {
            taxValueView.setText("");
        }
        if (amountValueView != null) {
            amountValueView.setText("");
        }
    }

    private void setSummaryValues(String yearValue, String taxValue, String amountValue) {
        if (hostValueView != null) {
            hostValueView.setText(valueOrUnknown(hostDisplay));
        }
        if (yearValueView != null) {
            yearValueView.setText(valueOrUnknown(yearValue));
        }
        if (taxValueView != null) {
            taxValueView.setText(valueOrUnknown(taxValue));
        }
        if (amountValueView != null) {
            amountValueView.setText(valueOrUnknown(amountValue));
        }
    }

    private void setupBackNavigation() {
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    private void showSignatureDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.signature_info_title)
                .setMessage(getString(
                        R.string.signature_info_message,
                        valueOrUnknown(salt),
                        valueOrUnknown(eddsaSignature),
                        valueOrUnknown(publicKey)))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String valueOrUnknown(String candidate) {
        return isEmpty(candidate) ? getString(R.string.value_unknown) : candidate;
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
        authorityPathSegments.clear();
        year = null;
        totalAmount = null;
        taxId = null;
        hostDisplay = null;
        salt = null;
        eddsaSignature = null;
        publicKey = null;
    }

    private SignatureStatus parseDonauUri(Uri uri) {
        host = uri.getHost();
        port = uri.getPort();
        if (isEmpty(host)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }

        List<String> segments = uri.getPathSegments();
        if (segments == null) {
            return SignatureStatus.INVALID_NUMBER_OF_ARGUMENTS;
        }

        authorityPathSegments.clear();

        if (segments.size() < 3) {
            return SignatureStatus.INVALID_NUMBER_OF_ARGUMENTS;
        }

        int yearIndex = segments.size() - 3;
        for (int i = 0; i < yearIndex; i++) {
            String segment = segments.get(i);
            String trimmed = segment != null ? segment.trim() : null;
            if (!isEmpty(trimmed)) {
                authorityPathSegments.add(trimmed);
            }
        }

        hostDisplay = buildHostDisplay();

        hostDisplay = buildHostDisplay();

        String yearCandidate = segments.get(yearIndex);
        if (yearCandidate != null) {
            yearCandidate = yearCandidate.trim();
        }
        if (!isFourDigitYear(yearCandidate)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }

        year = yearCandidate;

        String taxIdCandidate = segments.get(yearIndex + 1);
        if (taxIdCandidate != null) {
            taxIdCandidate = taxIdCandidate.trim();
        }
        if (!isValidTaxId(taxIdCandidate)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }
        taxId = taxIdCandidate;

        String saltCandidate = segments.get(yearIndex + 2);
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

        String publicKeyParam = uri.getQueryParameter("pub");
        publicKey = isEmpty(publicKeyParam) ? null : publicKeyParam.trim();

        return null;
    }

    private SignatureStatus ensurePublicKeyAvailable() {
        boolean insecureScheme = isInsecureScheme();
        boolean hasEmbeddedPub = !isEmpty(publicKey);
        if (insecureScheme) {
            if (!BuildConfig.ALLOW_INSECURE_HTTP) {
                return SignatureStatus.INSECURE_HTTP_UNSUPPORTED;
            }
            if (!isDeveloperModeEnabled()) {
                return SignatureStatus.INSECURE_HTTP_DISABLED;
            }
            if (hasEmbeddedPub && isTestingHost()) {
                return null;
            }
        }

        if (!insecureScheme && hasEmbeddedPub) {
            return null;
        }

        try {
            String fetchedKey = fetchSigningKey(insecureScheme);
            if (isEmpty(fetchedKey)) {
                return SignatureStatus.KEY_NOT_FOUND;
            }
            publicKey = fetchedKey;
            return null;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to download Donau signing keys", e);
            return SignatureStatus.KEY_DOWNLOAD_FAILED;
        }
    }

    private String buildHostDisplay() {
        if (isEmpty(host)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(host);
        if (port != -1) {
            builder.append(":").append(port);
        }
        for (String segment : authorityPathSegments) {
            if (!isEmpty(segment)) {
                builder.append("/").append(segment);
            }
        }
        return builder.toString();
    }

    private String fetchSigningKey(boolean insecure) throws IOException, JSONException {
        URL keysUrl = buildKeysUrl(insecure);
        if (keysUrl == null) {
            return null;
        }
        HttpURLConnection connection = (HttpURLConnection) keysUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }
            InputStream input = connection.getInputStream();
            String encoding = connection.getHeaderField("Content-Encoding");
            if (encoding != null) {
                if ("gzip".equalsIgnoreCase(encoding)) {
                    input = new GZIPInputStream(input);
                } else if ("deflate".equalsIgnoreCase(encoding)) {
                    input = new InflaterInputStream(input);
                }
            }
            String body;
            try {
                body = readStream(input);
            } finally {
                input.close();
            }
            JSONObject json = new JSONObject(body);
            JSONArray signkeys = json.optJSONArray("signkeys");
            if (signkeys == null) {
                return null;
            }
            for (int i = 0; i < signkeys.length(); i++) {
                JSONObject entry = signkeys.optJSONObject(i);
                if (entry != null) {
                    String keyCandidate = entry.optString("key", null);
                    if (isEmpty(keyCandidate) && entry.has("key")) {
                        JSONObject keyObj = entry.optJSONObject("key");
                        if (keyObj != null) {
                            keyCandidate = keyObj.optString("eddsa_pub", null);
                        }
                    }
                    if (!isEmpty(keyCandidate)) {
                        return keyCandidate.trim();
                    }
                } else {
                    String keyCandidate = signkeys.optString(i, null);
                    if (!isEmpty(keyCandidate)) {
                        return keyCandidate.trim();
                    }
                }
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private URL buildKeysUrl(boolean insecure) {
        if (isEmpty(host)) {
            return null;
        }
        try {
            Uri.Builder builder = new Uri.Builder()
                    .scheme(insecure ? "http" : "https")
                    .encodedAuthority(port != -1 ? host + ":" + port : host);
            for (String segment : authorityPathSegments) {
                if (!isEmpty(segment)) {
                    builder.appendPath(segment.trim());
                }
            }
            builder.appendPath("keys");
            return new URL(builder.build().toString());
        } catch (MalformedURLException | IllegalArgumentException e) {
            Log.e(TAG, "Invalid /keys URL", e);
            return null;
        }
    }

    private boolean isInsecureScheme() {
        return uriScheme != null && "donau+http".equalsIgnoreCase(uriScheme);
    }

    private boolean isDeveloperModeEnabled() {
        if (!BuildConfig.ENABLE_DEVELOPER_MODE) {
            return false;
        }
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean(SettingsActivity.KEY_DEVELOPER_MODE, false);
    }

    private boolean isTestingHost() {
        return host != null && host.equalsIgnoreCase("example.com");
    }

    private String readStream(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
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
