package com.anysoftkeyboard.remote.gif;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.List;

/**
 * A queryable GIF provider built from a {@code GifSourceConfig}. Implementations run synchronously
 * and are always invoked off the main thread (see {@code GifRepository}).
 */
public interface GifSource {

  /** The id of the {@code GifSourceConfig} this source was built from. */
  @NonNull
  String id();

  /** Human-readable label for badging results in the UI. */
  @NonNull
  String label();

  /**
   * Searches for GIFs matching {@code query}. Returns an empty list when nothing matches; throws on
   * transport/auth failures so {@code GifRepository} can fall through to the next source.
   */
  @NonNull
  List<GifResult> search(@NonNull String query, int limit) throws IOException;

  /**
   * Downloads the bytes at {@code url}, applying any source-specific auth (e.g. a giphery bearer
   * token). Used both for grid previews and for the full GIF; payloads are small enough to hold in
   * memory.
   */
  @NonNull
  byte[] download(@NonNull String url) throws IOException;

  @NonNull
  default byte[] downloadFullGif(@NonNull GifResult result) throws IOException {
    return download(result.getFullGifUrl());
  }

  @NonNull
  default byte[] downloadPreview(@NonNull GifResult result) throws IOException {
    return download(result.getPreviewUrl());
  }
}
