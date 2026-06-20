package com.anysoftkeyboard.remote.gif.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** {@link HttpTransport} backed by {@link HttpURLConnection} — no third-party dependency. */
public class UrlConnectionHttpTransport implements HttpTransport {

  private static final int CONNECT_TIMEOUT_MS = 4000;
  private static final int READ_TIMEOUT_MS = 6000;

  @NonNull
  @Override
  public HttpResponse get(@NonNull String url, @NonNull Map<String, String> headers)
      throws IOException {
    return request("GET", url, headers, null);
  }

  @NonNull
  @Override
  public HttpResponse post(
      @NonNull String url, @NonNull Map<String, String> headers, @Nullable String jsonBody)
      throws IOException {
    return request("POST", url, headers, jsonBody);
  }

  @NonNull
  private HttpResponse request(
      @NonNull String method,
      @NonNull String url,
      @NonNull Map<String, String> headers,
      @Nullable String body)
      throws IOException {
    final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    try {
      connection.setRequestMethod(method);
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      connection.setInstanceFollowRedirects(true);
      for (Map.Entry<String, String> header : headers.entrySet()) {
        connection.setRequestProperty(header.getKey(), header.getValue());
      }
      if (body != null) {
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        final byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = connection.getOutputStream()) {
          out.write(payload);
        }
      }

      final int code = connection.getResponseCode();
      final InputStream stream =
          (code >= 200 && code < 400) ? connection.getInputStream() : connection.getErrorStream();
      return new HttpResponse(code, readAll(stream));
    } finally {
      connection.disconnect();
    }
  }

  @NonNull
  private static byte[] readAll(@Nullable InputStream stream) throws IOException {
    if (stream == null) return new byte[0];
    try (InputStream in = stream) {
      final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      final byte[] chunk = new byte[8192];
      int read;
      while ((read = in.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      return buffer.toByteArray();
    }
  }
}
