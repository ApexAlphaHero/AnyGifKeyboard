package com.anysoftkeyboard.remote.gif.ui;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Decodes GIF bytes into a {@link Drawable} using only platform APIs (no Glide/Coil).
 *
 * <p>On API 28+ {@link ImageDecoder} produces an {@link AnimatedImageDrawable} that actually
 * animates; on older devices we fall back to the GIF's first frame via {@link BitmapFactory}.
 */
public final class GifImageLoader {

  private GifImageLoader() {}

  @NonNull
  public static Drawable decode(@NonNull Resources resources, @NonNull byte[] bytes)
      throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      final ImageDecoder.Source source = ImageDecoder.createSource(ByteBuffer.wrap(bytes));
      return ImageDecoder.decodeDrawable(source);
    }
    final BitmapDrawable drawable =
        new BitmapDrawable(resources, BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
    if (drawable.getBitmap() == null) {
      throw new IOException("Could not decode GIF preview");
    }
    return drawable;
  }

  /** Starts playback if the decoded drawable is animated. Must be called on the main thread. */
  public static void startIfAnimated(@NonNull Drawable drawable) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        && drawable instanceof AnimatedImageDrawable) {
      ((AnimatedImageDrawable) drawable).start();
    }
  }
}
