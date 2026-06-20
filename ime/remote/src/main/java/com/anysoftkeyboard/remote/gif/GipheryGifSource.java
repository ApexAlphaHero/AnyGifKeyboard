package com.anysoftkeyboard.remote.gif;

import android.net.Uri;
import androidx.annotation.NonNull;
import com.anysoftkeyboard.remote.gif.auth.GipheryTokenStore;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfig;
import com.anysoftkeyboard.remote.gif.net.HttpTransport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * GIF source backed by a self-hosted <a href="https://github.com/ApexAlphaHero/giphery">giphery</a>
 * server. Every request carries a bearer access token; on a {@code 401} the token is refreshed once
 * via {@link GipheryTokenStore} and the request retried.
 */
public class GipheryGifSource implements GifSource {

  private final String mId;
  private final String mLabel;
  private final String mBaseUrl;
  private final HttpTransport mTransport;
  private final GipheryTokenStore mTokenStore;

  public GipheryGifSource(
      @NonNull GifSourceConfig config,
      @NonNull HttpTransport transport,
      @NonNull GipheryTokenStore tokenStore) {
    mId = config.getId();
    mLabel = config.getLabel();
    mBaseUrl = trimTrailingSlash(config.getBaseUrl());
    mTransport = transport;
    mTokenStore = tokenStore;
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
    if (mBaseUrl.isEmpty()) return new ArrayList<>();

    final String url =
        mBaseUrl + "/api/v1/gifs?q=" + Uri.encode(query) + "&limit=" + limit;
    final HttpTransport.HttpResponse response = authorizedGet(url);
    if (!response.isSuccessful()) {
      throw new IOException("giphery search failed with HTTP " + response.code());
    }

    try {
      return parseSearch(response.string());
    } catch (JSONException e) {
      throw new IOException("Failed to parse giphery response", e);
    }
  }

  @NonNull
  private List<GifResult> parseSearch(@NonNull String body) throws JSONException {
    final List<GifResult> results = new ArrayList<>();
    final JSONArray items = new JSONObject(body).optJSONArray("items");
    if (items == null) return results;

    for (int i = 0; i < items.length(); i++) {
      final JSONObject gif = items.optJSONObject(i);
      if (gif == null) continue;
      final String rawUrl = gif.optString("raw_url", "");
      if (rawUrl.isEmpty()) continue;
      // raw_url is a server-relative path like "/api/v1/gifs/{id}/raw".
      final String absoluteUrl = rawUrl.startsWith("http") ? rawUrl : mBaseUrl + rawUrl;
      // giphery serves a single raw asset; use it for both preview and insertion.
      results.add(
          new GifResult(
              gif.optString("id", absoluteUrl),
              absoluteUrl,
              absoluteUrl,
              gif.optString("mime_type", "image/gif"),
              gif.optInt("width", 0),
              gif.optInt("height", 0),
              mId));
    }
    return results;
  }

  @NonNull
  @Override
  public byte[] download(@NonNull String url) throws IOException {
    final HttpTransport.HttpResponse response = authorizedGet(url);
    if (!response.isSuccessful()) {
      throw new IOException("giphery download failed with HTTP " + response.code());
    }
    return response.bytes();
  }

  /** GETs with a bearer token, refreshing once and retrying on a {@code 401}. */
  @NonNull
  private HttpTransport.HttpResponse authorizedGet(@NonNull String url) throws IOException {
    HttpTransport.HttpResponse response = mTransport.get(url, bearer(mTokenStore.accessToken()));
    if (response.code() == 401) {
      final String refreshed = mTokenStore.refresh();
      response = mTransport.get(url, bearer(refreshed));
    }
    return response;
  }

  @NonNull
  private static Map<String, String> bearer(@NonNull String accessToken) {
    final Map<String, String> headers = new HashMap<>();
    if (!accessToken.isEmpty()) {
      headers.put("Authorization", "Bearer " + accessToken);
    }
    return headers;
  }

  @NonNull
  private static String trimTrailingSlash(@NonNull String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
