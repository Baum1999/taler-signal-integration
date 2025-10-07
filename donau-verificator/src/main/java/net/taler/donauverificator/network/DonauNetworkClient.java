package net.taler.donauverificator.network;

import android.net.Uri;
import android.util.Log;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class DonauNetworkClient {

    private static final String TAG = "DonauNetworkClient";
    private static final int TIMEOUT_MS = 5000;

    private final boolean insecureScheme;
    private final String host;
    private final int port;
    private final List<String> authorityPathSegments;

    public DonauNetworkClient(boolean insecureScheme, String host, int port, List<String> authorityPathSegments) {
        this.insecureScheme = insecureScheme;
        this.host = host;
        this.port = port;
        if (authorityPathSegments == null || authorityPathSegments.isEmpty()) {
            this.authorityPathSegments = Collections.emptyList();
        } else {
            this.authorityPathSegments = Collections.unmodifiableList(new ArrayList<>(authorityPathSegments));
        }
    }

    public String fetchSigningKey(int targetYear) throws IOException, JSONException {
        URL url = buildUrl("keys");
        HttpURLConnection connection = openGetConnection(url);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new HttpStatusException(status, "Unexpected status for /keys: " + status);
            }
            String body = readStream(connection);
            JSONObject json = new JSONObject(body);
            JSONArray signkeys = json.optJSONArray("signkeys");
            if (signkeys == null) {
                return null;
            }
            String fallback = null;
            for (int i = 0; i < signkeys.length(); i++) {
                Object entry = signkeys.get(i);
                String keyCandidate = extractSigningKey(entry);
                if (keyCandidate == null) {
                    continue;
                }
                int yearCandidate = extractYear(entry);
                if (targetYear != Integer.MIN_VALUE && yearCandidate == targetYear) {
                    return keyCandidate;
                }
                if (fallback == null) {
                    fallback = keyCandidate;
                }
            }
            return fallback;
        } finally {
            connection.disconnect();
        }
    }

    public DonationStatement fetchDonationStatement(int year, String hashedDonorId) throws IOException, JSONException {
        URL url = buildUrl("donation-statement", String.valueOf(year), hashedDonorId);
        HttpURLConnection connection = openGetConnection(url);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new HttpStatusException(status, "Unexpected status for /donation-statement: " + status);
            }
            String body = readStream(connection);
            JSONObject json = new JSONObject(body);
            String total = trimToNull(json.optString("total", null));
            String signature = trimToNull(json.optString("donation_statement_sig", null));
            String pub = trimToNull(json.optString("donau_pub", null));
            if (total == null || signature == null || pub == null) {
                throw new JSONException("Incomplete donation statement response");
            }
            return new DonationStatement(total, signature, pub);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openGetConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        return connection;
    }

    private URL buildUrl(String... extraSegments) throws MalformedURLException {
        if (host == null || host.isEmpty()) {
            throw new MalformedURLException("Missing host");
        }
        Uri.Builder builder = new Uri.Builder()
                .scheme(insecureScheme ? "http" : "https")
                .encodedAuthority(port != -1 ? host + ":" + port : host);
        for (String segment : authorityPathSegments) {
            if (segment != null && !segment.trim().isEmpty()) {
                builder.appendPath(segment.trim());
            }
        }
        for (String segment : extraSegments) {
            builder.appendPath(segment);
        }
        return new URL(builder.build().toString());
    }

    private String readStream(HttpURLConnection connection) throws IOException {
        InputStream input = null;
        try {
            input = maybeWrap(connection.getInputStream(), connection.getHeaderField("Content-Encoding"));
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close input stream", e);
                }
            }
        }
    }

    private InputStream maybeWrap(InputStream input, String encoding) throws IOException {
        if (encoding == null) {
            return input;
        }
        if ("gzip".equalsIgnoreCase(encoding)) {
            return new GZIPInputStream(input);
        }
        if ("deflate".equalsIgnoreCase(encoding)) {
            return new InflaterInputStream(input);
        }
        return input;
    }

    private static String extractSigningKey(Object entry) {
        if (entry instanceof JSONObject) {
            JSONObject object = (JSONObject) entry;
            String direct = trimToNull(object.optString("key", null));
            if (direct != null) {
                return direct;
            }
            JSONObject keyObject = object.optJSONObject("key");
            if (keyObject != null) {
                String nested = trimToNull(keyObject.optString("eddsa_pub", null));
                if (nested != null) {
                    return nested;
                }
            }
            return trimToNull(object.optString("eddsa_pub", null));
        }
        if (entry instanceof String) {
            return trimToNull((String) entry);
        }
        return null;
    }

    private static int extractYear(Object entry) {
        if (entry instanceof JSONObject) {
            JSONObject object = (JSONObject) entry;
            if (object.has("year")) {
                try {
                    return object.getInt("year");
                } catch (JSONException e) {
                    return Integer.MIN_VALUE;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class DonationStatement {
        private final String total;
        private final String signature;
        private final String publicKey;

        public DonationStatement(String total, String signature, String publicKey) {
            this.total = total;
            this.signature = signature;
            this.publicKey = publicKey;
        }

        public String getTotal() {
            return total;
        }

        public String getSignature() {
            return signature;
        }

        public String getPublicKey() {
            return publicKey;
        }
    }

    public static final class HttpStatusException extends IOException {
        private final int statusCode;

        public HttpStatusException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
