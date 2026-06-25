package com.anysoftkeyboard.remote.gif;

import com.anysoftkeyboard.AnySoftKeyboardRobolectricTestRunner;
import com.anysoftkeyboard.remote.gif.auth.GipheryTokenStore;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfig;
import com.anysoftkeyboard.remote.gif.config.GifSourceType;
import com.anysoftkeyboard.remote.gif.net.HttpTransport;
import java.io.IOException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AnySoftKeyboardRobolectricTestRunner.class)
public class GipheryGifSourceTest {

  private static final String ITEMS_JSON =
      "{\"items\":[{\"id\":\"uuid1\",\"mime_type\":\"image/gif\",\"width\":640,\"height\":480,"
          + "\"raw_url\":\"/api/v1/gifs/uuid1/raw\"}],\"next_cursor\":null}";

  private static GifSourceConfig config(String access, String refresh) {
    return new GifSourceConfig(
        "giphery-1",
        GifSourceType.GIPHERY,
        "My GIFs",
        true,
        "https://gifs.example.com",
        "",
        access,
        refresh);
  }

  private GipheryGifSource source(GifSourceConfig config, HttpTransport transport) {
    return new GipheryGifSource(config, transport, new GipheryTokenStore(config, transport, null));
  }

  @Test
  public void testSearchBuildsAbsoluteUrlsAndBearer() throws Exception {
    final GifSourceConfig config = config("good-token", "refresh");
    final FakeHttpTransport transport =
        new FakeHttpTransport(
            (method, url, headers, body) -> FakeHttpTransport.response(200, ITEMS_JSON));

    final List<GifResult> results = source(config, transport).search("dogs", 30);

    Assert.assertEquals(1, results.size());
    Assert.assertEquals(
        "https://gifs.example.com/api/v1/gifs/uuid1/raw", results.get(0).getFullGifUrl());
    Assert.assertEquals(
        "Bearer good-token", transport.requestedHeaders.get(0).get("Authorization"));
    Assert.assertTrue(transport.requestedUrls.get(0).contains("q=dogs"));
  }

  @Test
  public void testRefreshesOnUnauthorizedThenRetries() throws Exception {
    final GifSourceConfig config = config("expired", "refresh");
    final boolean[] refreshed = {false};
    final FakeHttpTransport transport =
        new FakeHttpTransport(
            (method, url, headers, body) -> {
              if (method.equals("POST") && url.endsWith("/auth/refresh")) {
                refreshed[0] = true;
                return FakeHttpTransport.response(
                    200, "{\"access_token\":\"fresh\",\"refresh_token\":\"rotated\"}");
              }
              // GET search: fail until the token is refreshed.
              if (!refreshed[0]) return FakeHttpTransport.response(401, "");
              Assert.assertEquals("Bearer fresh", headers.get("Authorization"));
              return FakeHttpTransport.response(200, ITEMS_JSON);
            });

    final List<GifResult> results = source(config, transport).search("dogs", 30);

    Assert.assertTrue(refreshed[0]);
    Assert.assertEquals(1, results.size());
  }

  @Test(expected = GipheryTokenStore.ReauthRequiredException.class)
  public void testRejectedRefreshSurfacesReauth() throws Exception {
    final GifSourceConfig config = config("expired", "refresh");
    final FakeHttpTransport transport =
        new FakeHttpTransport(
            (method, url, headers, body) -> {
              if (method.equals("POST")) return FakeHttpTransport.response(401, "");
              return FakeHttpTransport.response(401, "");
            });

    source(config, transport).search("dogs", 30);
  }

  @Test
  public void testEmptyBaseUrlReturnsEmpty() throws IOException {
    final GifSourceConfig config =
        new GifSourceConfig("g", GifSourceType.GIPHERY, "x", true, "", "", "tok", "ref");
    final FakeHttpTransport transport =
        new FakeHttpTransport(
            (method, url, headers, body) -> {
              throw new AssertionError("should not hit network with empty base url");
            });

    Assert.assertTrue(source(config, transport).search("dogs", 30).isEmpty());
  }
}
