package com.anysoftkeyboard.remote.gif.config;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.anysoftkeyboard.base.utils.Logger;
import com.anysoftkeyboard.prefs.DirectBootAwareSharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Persists the user's ordered list of {@link GifSourceConfig}s as a single JSON-array string in
 * {@link SharedPreferences}. List order is the search priority. Shared by the search runtime (in
 * {@code :ime:remote}) and the settings UI (in {@code :ime:app}), so both see the same list.
 */
public class GifSourceConfigStore {
  private static final String TAG = "GifSourceConfigStore";
  @VisibleForTesting static final String PREF_KEY = "gif_sources_v1";

  private final SharedPreferences mSharedPreferences;

  public GifSourceConfigStore(@NonNull SharedPreferences sharedPreferences) {
    mSharedPreferences = sharedPreferences;
  }

  @NonNull
  public static GifSourceConfigStore create(@NonNull Context context) {
    return new GifSourceConfigStore(DirectBootAwareSharedPreferences.create(context));
  }

  /** All configured sources, in priority order. */
  @NonNull
  public List<GifSourceConfig> getAll() {
    final List<GifSourceConfig> result = new ArrayList<>();
    final String raw = mSharedPreferences.getString(PREF_KEY, "");
    if (raw == null || raw.isEmpty()) return result;
    try {
      final JSONArray array = new JSONArray(raw);
      for (int i = 0; i < array.length(); i++) {
        final JSONObject item = array.optJSONObject(i);
        if (item == null) continue;
        final GifSourceConfig config = GifSourceConfig.fromJson(item);
        if (config != null) result.add(config);
      }
    } catch (JSONException e) {
      Logger.w(TAG, e, "Failed to parse stored GIF sources. Returning empty list.");
    }
    return result;
  }

  /** Only the enabled sources, in priority order. This is what {@code GifRepository} queries. */
  @NonNull
  public List<GifSourceConfig> getEnabledInOrder() {
    final List<GifSourceConfig> enabled = new ArrayList<>();
    for (GifSourceConfig config : getAll()) {
      if (config.isEnabled()) enabled.add(config);
    }
    return enabled;
  }

  @Nullable
  public GifSourceConfig get(@NonNull String id) {
    for (GifSourceConfig config : getAll()) {
      if (config.getId().equals(id)) return config;
    }
    return null;
  }

  /** Replaces the whole list (and thereby its order). */
  public void save(@NonNull List<GifSourceConfig> configs) {
    final JSONArray array = new JSONArray();
    for (GifSourceConfig config : configs) {
      try {
        array.put(config.toJson());
      } catch (JSONException e) {
        Logger.w(TAG, e, "Failed to serialize GIF source %s. Skipping.", config.getId());
      }
    }
    mSharedPreferences.edit().putString(PREF_KEY, array.toString()).apply();
  }

  /** Adds the source, or replaces the existing one with the same id (keeping its position). */
  public void upsert(@NonNull GifSourceConfig config) {
    final List<GifSourceConfig> all = getAll();
    for (int i = 0; i < all.size(); i++) {
      if (all.get(i).getId().equals(config.getId())) {
        all.set(i, config);
        save(all);
        return;
      }
    }
    all.add(config);
    save(all);
  }

  public void delete(@NonNull String id) {
    final List<GifSourceConfig> all = getAll();
    final List<GifSourceConfig> kept = new ArrayList<>(all.size());
    for (GifSourceConfig config : all) {
      if (!config.getId().equals(id)) kept.add(config);
    }
    save(kept);
  }

  public void setEnabled(@NonNull String id, boolean enabled) {
    final GifSourceConfig existing = get(id);
    if (existing != null) upsert(existing.withEnabled(enabled));
  }
}
