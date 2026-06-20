package com.anysoftkeyboard.remote.gif;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.anysoftkeyboard.remote.gif.net.HttpTransport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Programmable {@link HttpTransport} for tests. Records requests and delegates to a handler. */
class FakeHttpTransport implements HttpTransport {

  interface Handler {
    @NonNull
    HttpResponse handle(
        @NonNull String method,
        @NonNull String url,
        @NonNull Map<String, String> headers,
        @Nullable String body)
        throws IOException;
  }

  final List<String> requestedUrls = new ArrayList<>();
  final List<Map<String, String>> requestedHeaders = new ArrayList<>();
  private final Handler mHandler;

  FakeHttpTransport(@NonNull Handler handler) {
    mHandler = handler;
  }

  @NonNull
  @Override
  public HttpResponse get(@NonNull String url, @NonNull Map<String, String> headers)
      throws IOException {
    requestedUrls.add(url);
    requestedHeaders.add(headers);
    return mHandler.handle("GET", url, headers, null);
  }

  @NonNull
  @Override
  public HttpResponse post(
      @NonNull String url, @NonNull Map<String, String> headers, @Nullable String jsonBody)
      throws IOException {
    requestedUrls.add(url);
    requestedHeaders.add(headers);
    return mHandler.handle("POST", url, headers, jsonBody);
  }

  @NonNull
  static HttpResponse response(int code, @NonNull String body) {
    return new HttpResponse(code, body.getBytes(StandardCharsets.UTF_8));
  }
}
