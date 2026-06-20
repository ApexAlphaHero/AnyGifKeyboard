package com.anysoftkeyboard.remote.gif;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.anysoftkeyboard.remote.gif.auth.GipheryTokenStore;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfig;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfigStore;
import com.anysoftkeyboard.remote.gif.net.HttpTransport;
import com.anysoftkeyboard.remote.gif.net.UrlConnectionHttpTransport;

/** Builds a {@link GifSource} from a {@link GifSourceConfig}. Add a case here for new source types. */
public class GifSourceFactory {

  private final HttpTransport mTransport;
  @Nullable private final GifSourceConfigStore mConfigStore;

  public GifSourceFactory() {
    this(new UrlConnectionHttpTransport(), null);
  }

  public GifSourceFactory(
      @NonNull HttpTransport transport, @Nullable GifSourceConfigStore configStore) {
    mTransport = transport;
    mConfigStore = configStore;
  }

  @NonNull
  public GifSource create(@NonNull GifSourceConfig config) {
    switch (config.getType()) {
      case GIPHERY:
        return new GipheryGifSource(
            config, mTransport, new GipheryTokenStore(config, mTransport, mConfigStore));
      case GIPHY:
        return new GiphyGifSource(config, mTransport);
      default:
        throw new IllegalArgumentException("Unsupported GIF source type: " + config.getType());
    }
  }
}
