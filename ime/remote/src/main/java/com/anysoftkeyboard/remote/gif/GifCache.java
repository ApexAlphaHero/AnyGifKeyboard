package com.anysoftkeyboard.remote.gif;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Writes downloaded GIF bytes to the app-private {@code media/} folder and returns a {@code
 * content://} Uri served by the module's {@code FileProvider} — the same folder/authority the
 * existing media pipeline uses, so the resulting Uri can be committed straight into the editor.
 */
public final class GifCache {

  private GifCache() {}

  @NonNull
  public static Uri save(@NonNull Context context, @NonNull byte[] bytes, @NonNull String id)
      throws IOException {
    final File mediaFolder = new File(context.getFilesDir(), "media");
    if (!mediaFolder.isDirectory() && !mediaFolder.mkdirs()) {
      throw new IOException("Could not create media folder");
    }
    final String safeName =
        String.format(Locale.ROOT, "gif_%s.gif", Integer.toHexString(id.hashCode()));
    final File target = new File(mediaFolder, safeName);
    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
      out.write(bytes);
    }
    return FileProvider.getUriForFile(context, context.getPackageName(), target);
  }
}
