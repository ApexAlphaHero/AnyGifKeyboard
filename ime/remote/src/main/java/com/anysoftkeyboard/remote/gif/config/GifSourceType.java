package com.anysoftkeyboard.remote.gif.config;

/**
 * The kind of GIF provider a {@link GifSourceConfig} represents.
 *
 * <p>New provider types (e.g. Tenor) are added by introducing a value here and a matching {@code
 * GifSource} implementation in {@link com.anysoftkeyboard.remote.gif.GifSourceFactory}.
 */
public enum GifSourceType {
  /** A self-hosted <a href="https://github.com/ApexAlphaHero/giphery">giphery</a> server. */
  GIPHERY,
  /** The public <a href="https://developers.giphy.com/">GIPHY</a> HTTP API. */
  GIPHY
}
