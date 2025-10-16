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

import net.taler.donauverificator.network.CrockfordBase32;
import net.taler.donauverificator.network.DonauNetworkClient;
import net.taler.donauverificator.network.DonauNetworkClient.DonationStatement;
import net.taler.donauverificator.network.DonauNetworkClient.HttpStatusException;

import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Results extends AppCompatActivity {
    static {
        System.loadLibrary("verification");
    }

    private static final String TAG = "Results";

    // Format: donau://base/year/taxid-enc/salt?total=...&sig=ED25519:...
    // CrockfordBase32 encoded: SIGNATURE, PUBLICKEY

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
    private final List<String> publicKeys = new ArrayList<>();
    private DonauNetworkClient networkClient;
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
        DONATION_STATEMENT_DOWNLOAD_FAILED,
        DONATION_STATEMENT_NOT_FOUND,
        DONATION_STATEMENT_INVALID,
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
        if (!isEmpty(publicKey)) {
            int res = ed25519_verify(year, totalAmount, taxId, salt, eddsaSignature, publicKey);
            if (res == 0) {
                statusHandling(SignatureStatus.SIGNATURE_VALID);
            } else {
                statusHandling(SignatureStatus.SIGNATURE_INVALID);
            }
            return;
        }

        if (publicKeys.isEmpty()) {
            statusHandling(SignatureStatus.SIGNATURE_INVALID);
            return;
        }

        for (String candidate : publicKeys) {
            if (isEmpty(candidate)) continue;
            int res = ed25519_verify(year, totalAmount, taxId, salt, eddsaSignature, candidate);
            if (res == 0) {
                publicKey = candidate; // remember the matching key for UI/details
                statusHandling(SignatureStatus.SIGNATURE_VALID);
                return;
            }
        }
        statusHandling(SignatureStatus.SIGNATURE_INVALID);
    }

    private void startVerificationAsync() {
        new Thread(() -> {
            SignatureStatus statusResult = ensurePublicKeyAvailable();
            if (statusResult == null) {
                statusResult = ensureDonationStatementAvailable();
            }
            SignatureStatus finalStatus = statusResult;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalStatus != null) {
                    statusHandling(finalStatus);
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
            case DONATION_STATEMENT_DOWNLOAD_FAILED:
                updateStatusCard(R.string.status_donation_statement_download_failed, R.color.validation_surface_error, R.color.red, false);
                break;
            case DONATION_STATEMENT_NOT_FOUND:
                updateStatusCard(R.string.status_donation_statement_not_found, R.color.validation_surface_info, R.color.colorSecondary, false);
                break;
            case DONATION_STATEMENT_INVALID:
                updateStatusCard(R.string.status_donation_statement_invalid, R.color.validation_surface_error, R.color.red, false);
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
        String lineBreak = "\n";
        String message = getString(R.string.signature_info_salt, valueOrUnknown(salt))
                + lineBreak
                + getString(R.string.signature_info_signature, valueOrUnknown(eddsaSignature))
                + lineBreak
                + getString(R.string.signature_info_public_key, valueOrUnknown(publicKey));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.signature_info_title)
                .setMessage(message)
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
        publicKeys.clear();
        networkClient = null;
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

        String yearCandidate = segments.get(yearIndex);
        if (yearCandidate != null) {
            yearCandidate = yearCandidate.trim();
        }
        if (!isFourDigitYear(yearCandidate)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }

        year = yearCandidate;

        // Tax ID: use exact UTF-8 bytes from percent-decoded segment without trimming
        String taxIdCandidate = segments.get(yearIndex + 1);
        if (isEmpty(taxIdCandidate)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }
        taxId = taxIdCandidate;

        String saltCandidate = segments.get(yearIndex + 2);
        if (saltCandidate != null) {
            saltCandidate = saltCandidate.trim();
        }
        if (isEmpty(saltCandidate)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }
        salt = saltCandidate;

        String totalParam = uri.getQueryParameter("total");
        if (totalParam != null) {
            String trimmedTotal = totalParam.trim();
            if (trimmedTotal.isEmpty()) {
                return SignatureStatus.MALFORMED_ARGUMENT;
            }
            totalAmount = trimmedTotal;
        } else {
            totalAmount = null;
        }

        String sigParam = uri.getQueryParameter("sig");
        if (sigParam != null) {
            eddsaSignature = extractEd25519Signature(sigParam);
            if (isEmpty(eddsaSignature)) {
                return SignatureStatus.MALFORMED_ARGUMENT;
            }
        } else {
            eddsaSignature = null;
        }

        String publicKeyParam = uri.getQueryParameter("pub");
        publicKey = isEmpty(publicKeyParam) ? null : publicKeyParam.trim();

        networkClient = new DonauNetworkClient(isInsecureScheme(), host, port, authorityPathSegments);

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
            DonauNetworkClient client = getNetworkClient();
            List<String> fetched = client.fetchSigningKeys();
            if (fetched == null || fetched.isEmpty()) {
                return SignatureStatus.KEY_NOT_FOUND;
            }
            publicKeys.clear();
            publicKeys.addAll(fetched);
            return null;
        } catch (HttpStatusException e) {
            Log.e(TAG, "Failed to download Donau signing keys, HTTP " + e.getStatusCode(), e);
            return SignatureStatus.KEY_DOWNLOAD_FAILED;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to download Donau signing keys", e);
            return SignatureStatus.KEY_DOWNLOAD_FAILED;
        }
    }

    private SignatureStatus ensureDonationStatementAvailable() {
        boolean needsTotal = isEmpty(totalAmount);
        boolean needsSignature = isEmpty(eddsaSignature);
        if (!needsTotal && !needsSignature) {
            return null;
        }
        if (isEmpty(taxId) || isEmpty(salt) || isEmpty(year)) {
            return SignatureStatus.MALFORMED_ARGUMENT;
        }
        try {
            String donorHash = computeDonorHash(taxId, salt);
            DonauNetworkClient client = getNetworkClient();
            int donationYear = parseYearOrDefault(year);
            DonationStatement statement = client.fetchDonationStatement(donationYear, donorHash);
            String statementTotal = statement.total();
            String statementSignature = statement.signature();
            String statementPublicKey = statement.publicKey();
            if (isEmpty(statementTotal) || isEmpty(statementSignature) || isEmpty(statementPublicKey)) {
                Log.e(TAG, "Donation statement response missing required fields");
                return SignatureStatus.DONATION_STATEMENT_INVALID;
            }
            if (!needsTotal && totalAmount != null && !totalAmount.equals(statementTotal)) {
                Log.e(TAG, "Donation statement total mismatch");
                return SignatureStatus.DONATION_STATEMENT_INVALID;
            }
            if (!isEmpty(publicKey) && !publicKey.equals(statementPublicKey)) {
                Log.e(TAG, "Donation statement public key mismatch");
                return SignatureStatus.DONATION_STATEMENT_INVALID;
            }
            String extractedSignature = extractEd25519Signature(statementSignature);
            String normalizedSignature;
            if (extractedSignature != null) {
                normalizedSignature = extractedSignature;
            } else {
                if (statementSignature.contains(":") || statementSignature.contains("=")) {
                    Log.e(TAG, "Donation statement signature format invalid");
                    return SignatureStatus.DONATION_STATEMENT_INVALID;
                }
                normalizedSignature = statementSignature.trim();
            }
            if (!needsSignature && eddsaSignature != null && !eddsaSignature.equals(normalizedSignature)) {
                Log.e(TAG, "Donation statement signature mismatch");
                return SignatureStatus.DONATION_STATEMENT_INVALID;
            }
            totalAmount = statementTotal;
            eddsaSignature = normalizedSignature;
            publicKey = statementPublicKey;
            return null;
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return SignatureStatus.DONATION_STATEMENT_NOT_FOUND;
            }
            Log.e(TAG, "Donation statement download failed, HTTP " + e.getStatusCode(), e);
            return SignatureStatus.DONATION_STATEMENT_DOWNLOAD_FAILED;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Donation statement download failed", e);
            return SignatureStatus.DONATION_STATEMENT_DOWNLOAD_FAILED;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Unable to hash donor identifier", e);
            return SignatureStatus.DONATION_STATEMENT_INVALID;
        }
    }

    private DonauNetworkClient getNetworkClient() {
        if (networkClient == null) {
            networkClient = new DonauNetworkClient(isInsecureScheme(), host, port, authorityPathSegments);
        }
        return networkClient;
    }

    private int parseYearOrDefault(String value) {
        if (isEmpty(value)) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private String computeDonorHash(String taxIdValue, String saltValue) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        // H = SHA-512( UTF-8(taxId) || 0x00 || UTF-8(salt) || 0x00 )
        digest.update(taxIdValue.getBytes(StandardCharsets.UTF_8));
        digest.update(new byte[]{0});
        digest.update(saltValue.getBytes(StandardCharsets.UTF_8));
        digest.update(new byte[]{0});
        byte[] hash = digest.digest();
        return CrockfordBase32.encode(hash);
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
}
