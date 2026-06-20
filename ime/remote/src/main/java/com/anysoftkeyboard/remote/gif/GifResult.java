package com.anysoftkeyboard.remote.gif;

import androidx.annotation.NonNull;

/**
 * A single GIF returned from a {@link GifSource} search.
 *
 * <p>{@link #getPreviewUrl()} is a small/animated variant shown in the search grid, while {@link
 * #getFullGifUrl()} is the full GIF that gets inserted into the editor. {@link #getSourceId()} ties
 * the result back to the configured source it came from, so the right transport (e.g. an
 * authenticated giphery request vs. a public GIPHY CDN link) is used when downloading it.
 */
public class GifResult {
  private final String mId;
  private final String mPreviewUrl;
  private final String mFullGifUrl;
  private final String mMimeType;
  private final int mWidth;
  private final int mHeight;
  private final String mSourceId;

  public GifResult(
      @NonNull String id,
      @NonNull String previewUrl,
      @NonNull String fullGifUrl,
      @NonNull String mimeType,
      int width,
      int height,
      @NonNull String sourceId) {
    mId = id;
    mPreviewUrl = previewUrl;
    mFullGifUrl = fullGifUrl;
    mMimeType = mimeType;
    mWidth = width;
    mHeight = height;
    mSourceId = sourceId;
  }

  @NonNull
  public String getId() {
    return mId;
  }

  @NonNull
  public String getPreviewUrl() {
    return mPreviewUrl;
  }

  @NonNull
  public String getFullGifUrl() {
    return mFullGifUrl;
  }

  @NonNull
  public String getMimeType() {
    return mMimeType;
  }

  public int getWidth() {
    return mWidth;
  }

  public int getHeight() {
    return mHeight;
  }

  @NonNull
  public String getSourceId() {
    return mSourceId;
  }
}
