package com.anysoftkeyboard.remote.gif.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfig;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfigStore;
import com.anysoftkeyboard.remote.gif.net.HttpTransport;
import java.io.IOException;
import java.util.Collections;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Holds and refreshes a giphery source's JWTs. giphery access tokens are short-lived (~15 min);
 * when a request gets a {@code 401} the source calls {@link #refresh()}, which posts the (rotating)
 * refresh token to {@code /auth/refresh}, persists the new pair via {@link GifSourceConfigStore},
 * and returns the new access token.
 *
 * <p>Token field names follow a typical FastAPI JWT scheme ({@code access_token} / {@code
 * refresh_token}); adjust here if your giphery server differs.
 *
 * <p>Tokens are stored in plain {@code SharedPreferences} (via the config store). Wrapping that in
 * {@code androidx.security.EncryptedSharedPreferences} would harden at-rest storage but adds a
 * third-party dependency, so it is intentionally left out of v1.
 */
public class GipheryTokenStore {

  /** Raised when the refresh token itself is rejected and the source must be re-paired. */
  public static class ReauthRequiredException extends IOException {
    private static final long serialVersionUID = 1L;

    public ReauthRequiredException(String message) {
      super(message);
    }
  }

  private final HttpTransport mTransport;
  @Nullable private final GifSourceConfigStore mConfigStore;
  private final String mSourceId;
  private final String mBaseUrl;

  private String mAccessToken;
  private String mRefreshToken;

  public GipheryTokenStore(
      @NonNull GifSourceConfig config,
      @NonNull HttpTransport transport,
      @Nullable GifSourceConfigStore configStore) {
    mTransport = transport;
    mConfigStore = configStore;
    mSourceId = config.getId();
    mBaseUrl = trimTrailingSlash(config.getBaseUrl());
    mAccessToken = config.getAccessToken();
    mRefreshToken = config.getRefreshToken();
  }

  @NonNull
  public synchronized String accessToken() {
    return mAccessToken;
  }

  @NonNull
  public synchronized String refresh() throws IOException {
    if (mRefreshToken.isEmpty()) {
      throw new ReauthRequiredException("No refresh token for giphery source " + mSourceId);
    }

    final String body;
    try {
      body = new JSONObject().put("refresh_token", mRefreshToken).toString();
    } catch (JSONException e) {
      throw new IOException("Failed to build refresh request", e);
    }

    final HttpTransport.HttpResponse response =
        mTransport.post(mBaseUrl + "/auth/refresh", Collections.emptyMap(), body);
    if (response.code() == 401 || response.code() == 403) {
      throw new ReauthRequiredException(
          "giphery refresh token rejected (HTTP " + response.code() + ")");
    }
    if (!response.isSuccessful()) {
      throw new IOException("giphery refresh failed with HTTP " + response.code());
    }

    try {
      final JSONObject json = new JSONObject(response.string());
      final String newAccess = json.optString("access_token", "");
      if (newAccess.isEmpty()) {
        throw new IOException("giphery refresh response missing access_token");
      }
      // refresh tokens rotate; fall back to the existing one if the server didn't return a new one.
      final String newRefresh = json.optString("refresh_token", mRefreshToken);
      updateTokens(newAccess, newRefresh);
      return newAccess;
    } catch (JSONException e) {
      throw new IOException("Failed to parse giphery refresh response", e);
    }
  }

  private synchronized void updateTokens(@NonNull String access, @NonNull String refresh) {
    mAccessToken = access;
    mRefreshToken = refresh;
    if (mConfigStore != null) {
      final GifSourceConfig current = mConfigStore.get(mSourceId);
      if (current != null) {
        mConfigStore.upsert(current.withTokens(access, refresh));
      }
    }
  }

  @NonNull
  private static String trimTrailingSlash(@NonNull String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
