package com.anysoftkeyboard.remote.gif.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One user-configured GIF source. Immutable; mutate via the {@code with*} copy methods.
 *
 * <p>Persisted as a JSON object inside the ordered array managed by {@link GifSourceConfigStore};
 * the array index is the search priority. Type-specific fields are kept on the same flat object
 * (giphery uses {@link #getBaseUrl()} + the auth tokens; GIPHY uses {@link #getApiKey()}).
 */
public class GifSourceConfig {
  private final String mId;
  private final GifSourceType mType;
  private final String mLabel;
  private final boolean mEnabled;
  private final String mBaseUrl;
  private final String mApiKey;
  private final String mAccessToken;
  private final String mRefreshToken;

  public GifSourceConfig(
      @NonNull String id,
      @NonNull GifSourceType type,
      @NonNull String label,
      boolean enabled,
      @NonNull String baseUrl,
      @NonNull String apiKey,
      @NonNull String accessToken,
      @NonNull String refreshToken) {
    mId = id;
    mType = type;
    mLabel = label;
    mEnabled = enabled;
    mBaseUrl = baseUrl;
    mApiKey = apiKey;
    mAccessToken = accessToken;
    mRefreshToken = refreshToken;
  }

  @NonNull
  public String getId() {
    return mId;
  }

  @NonNull
  public GifSourceType getType() {
    return mType;
  }

  @NonNull
  public String getLabel() {
    return mLabel;
  }

  public boolean isEnabled() {
    return mEnabled;
  }

  @NonNull
  public String getBaseUrl() {
    return mBaseUrl;
  }

  @NonNull
  public String getApiKey() {
    return mApiKey;
  }

  @NonNull
  public String getAccessToken() {
    return mAccessToken;
  }

  @NonNull
  public String getRefreshToken() {
    return mRefreshToken;
  }

  @NonNull
  public GifSourceConfig withEnabled(boolean enabled) {
    return new GifSourceConfig(
        mId, mType, mLabel, enabled, mBaseUrl, mApiKey, mAccessToken, mRefreshToken);
  }

  /** Returns a copy with refreshed giphery tokens (used after a token refresh). */
  @NonNull
  public GifSourceConfig withTokens(@NonNull String accessToken, @NonNull String refreshToken) {
    return new GifSourceConfig(
        mId, mType, mLabel, mEnabled, mBaseUrl, mApiKey, accessToken, refreshToken);
  }

  @NonNull
  public JSONObject toJson() throws JSONException {
    final JSONObject json = new JSONObject();
    json.put("id", mId);
    json.put("type", mType.name());
    json.put("label", mLabel);
    json.put("enabled", mEnabled);
    json.put("baseUrl", mBaseUrl);
    json.put("apiKey", mApiKey);
    json.put("accessToken", mAccessToken);
    json.put("refreshToken", mRefreshToken);
    return json;
  }

  /**
   * Parses a config object; returns {@code null} for an unknown/invalid type so callers skip it.
   */
  @Nullable
  public static GifSourceConfig fromJson(@NonNull JSONObject json) {
    final GifSourceType type;
    try {
      type = GifSourceType.valueOf(json.optString("type", ""));
    } catch (IllegalArgumentException e) {
      return null;
    }
    final String id = json.optString("id", "");
    if (id.isEmpty()) return null;
    return new GifSourceConfig(
        id,
        type,
        json.optString("label", type.name()),
        json.optBoolean("enabled", true),
        json.optString("baseUrl", ""),
        json.optString("apiKey", ""),
        json.optString("accessToken", ""),
        json.optString("refreshToken", ""));
  }
}
