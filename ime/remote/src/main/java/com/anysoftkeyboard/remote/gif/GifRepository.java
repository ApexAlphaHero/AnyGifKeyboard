package com.anysoftkeyboard.remote.gif;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.anysoftkeyboard.base.utils.Logger;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfig;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfigStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries the user's enabled GIF sources in priority order and returns the first source that yields
 * results. A source that returns nothing, errors, or times out is skipped and the next is tried; if
 * every source comes up empty the result list is empty.
 *
 * <p>All methods are synchronous and meant to run off the main thread (the search activity wraps
 * them in an Rx background call).
 */
public class GifRepository {
  private static final String TAG = "GifRepository";

  private final List<GifSource> mSources;
  private final Map<String, GifSource> mSourcesById = new LinkedHashMap<>();

  public GifRepository(@NonNull List<GifSource> sources) {
    mSources = sources;
    for (GifSource source : sources) {
      mSourcesById.put(source.id(), source);
    }
  }

  /** Builds a repository from the enabled sources, in their configured order. */
  @NonNull
  public static GifRepository fromStore(
      @NonNull GifSourceConfigStore store, @NonNull GifSourceFactory factory) {
    final List<GifSource> sources = new ArrayList<>();
    for (GifSourceConfig config : store.getEnabledInOrder()) {
      sources.add(factory.create(config));
    }
    return new GifRepository(sources);
  }

  public boolean hasSources() {
    return !mSources.isEmpty();
  }

  /** Returns results from the first source (in priority order) that has any; empty otherwise. */
  @NonNull
  public List<GifResult> search(@NonNull String query, int limit) {
    for (GifSource source : mSources) {
      try {
        final List<GifResult> results = source.search(query, limit);
        if (!results.isEmpty()) {
          Logger.d(TAG, "Source '%s' returned %d results", source.id(), results.size());
          return results;
        }
        Logger.d(TAG, "Source '%s' had no results; trying next", source.id());
      } catch (Exception e) {
        Logger.w(TAG, e, "Source '%s' failed; trying next", source.id());
      }
    }
    return Collections.emptyList();
  }

  /** The source a result came from, used to download it with the correct auth. */
  @Nullable
  public GifSource sourceFor(@NonNull GifResult result) {
    return mSourcesById.get(result.getSourceId());
  }
}
