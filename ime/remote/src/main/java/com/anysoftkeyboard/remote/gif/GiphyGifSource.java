package com.anysoftkeyboard.remote.gif;

import android.net.Uri;
import androidx.annotation.NonNull;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfig;
import com.anysoftkeyboard.remote.gif.net.HttpTransport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * GIF source backed by the public <a href="https://developers.giphy.com/docs/api/">GIPHY HTTP
 * API</a>. Requires a REST API key (not an SDK key). Image URLs are public CDN links, so {@link
 * #download(String)} needs no auth header.
 */
public class GiphyGifSource implements GifSource {
  private static final String SEARCH_ENDPOINT = "https://api.giphy.com/v1/gifs/search";

  private final String mId;
  private final String mLabel;
  private final String mApiKey;
  private final HttpTransport mTransport;

  public GiphyGifSource(@NonNull GifSourceConfig config, @NonNull HttpTransport transport) {
    mId = config.getId();
    mLabel = config.getLabel();
    mApiKey = config.getApiKey();
    mTransport = transport;
  }

  @NonNull
  @Override
  public String id() {
    return mId;
  }

  @NonNull
  @Override
  public String label() {
    return mLabel;
  }

  @NonNull
  @Override
  public List<GifResult> search(@NonNull String query, int limit) throws IOException {
    if (mApiKey.isEmpty()) return Collections.emptyList();

    final String url =
        SEARCH_ENDPOINT
            + "?api_key="
            + Uri.encode(mApiKey)
            + "&q="
            + Uri.encode(query)
            + "&limit="
            + limit
            + "&rating=g";

    final HttpTransport.HttpResponse response = mTransport.get(url, Collections.emptyMap());
    if (!response.isSuccessful()) {
      throw new IOException("GIPHY search failed with HTTP " + response.code());
    }

    try {
      return parseSearch(response.string());
    } catch (JSONException e) {
      throw new IOException("Failed to parse GIPHY response", e);
    }
  }

  @NonNull
  private List<GifResult> parseSearch(@NonNull String body) throws JSONException {
    final List<GifResult> results = new ArrayList<>();
    final JSONArray data = new JSONObject(body).optJSONArray("data");
    if (data == null) return results;

    for (int i = 0; i < data.length(); i++) {
      final JSONObject gif = data.optJSONObject(i);
      if (gif == null) continue;
      final JSONObject images = gif.optJSONObject("images");
      if (images == null) continue;

      final JSONObject preview = firstNonNull(images, "fixed_width", "downsized", "original");
      final JSONObject full = firstNonNull(images, "original", "downsized", "fixed_width");
      if (preview == null || full == null) continue;

      final String previewUrl = preview.optString("url", "");
      final String fullUrl = full.optString("url", "");
      if (previewUrl.isEmpty() || fullUrl.isEmpty()) continue;

      results.add(
          new GifResult(
              gif.optString("id", fullUrl),
              previewUrl,
              fullUrl,
              "image/gif",
              parseIntSafe(full.optString("width")),
              parseIntSafe(full.optString("height")),
              mId));
    }
    return results;
  }

  @NonNull
  @Override
  public byte[] download(@NonNull String url) throws IOException {
    final HttpTransport.HttpResponse response = mTransport.get(url, Collections.emptyMap());
    if (!response.isSuccessful()) {
      throw new IOException("GIPHY download failed with HTTP " + response.code());
    }
    return response.bytes();
  }

  private static JSONObject firstNonNull(@NonNull JSONObject images, @NonNull String... keys) {
    for (String key : keys) {
      final JSONObject candidate = images.optJSONObject(key);
      if (candidate != null && !candidate.optString("url", "").isEmpty()) return candidate;
    }
    return null;
  }

  private static int parseIntSafe(@NonNull String value) {
    try {
      return value.isEmpty() ? 0 : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
