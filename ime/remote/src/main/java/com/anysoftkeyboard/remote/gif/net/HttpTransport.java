package com.anysoftkeyboard.remote.gif.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

/**
 * Minimal HTTP abstraction so GIF sources can be unit-tested with a fake transport instead of real
 * network. The production implementation is {@link UrlConnectionHttpTransport} ({@code
 * HttpURLConnection}); no third-party HTTP library is pulled in.
 */
public interface HttpTransport {

  @NonNull
  HttpResponse get(@NonNull String url, @NonNull Map<String, String> headers) throws IOException;

  @NonNull
  HttpResponse post(
      @NonNull String url, @NonNull Map<String, String> headers, @Nullable String jsonBody)
      throws IOException;

  /** A fully-buffered HTTP response. GIF payloads are small enough to hold in memory. */
  final class HttpResponse {
    private final int mCode;
    private final byte[] mBody;

    public HttpResponse(int code, @NonNull byte[] body) {
      mCode = code;
      mBody = body;
    }

    public int code() {
      return mCode;
    }

    public boolean isSuccessful() {
      return mCode >= 200 && mCode < 300;
    }

    @NonNull
    public byte[] bytes() {
      return mBody;
    }

    @NonNull
    public String string() {
      return new String(mBody, java.nio.charset.StandardCharsets.UTF_8);
    }
  }
}
