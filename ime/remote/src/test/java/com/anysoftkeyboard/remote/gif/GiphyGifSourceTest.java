package com.anysoftkeyboard.remote.gif;

import com.anysoftkeyboard.AnySoftKeyboardRobolectricTestRunner;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfig;
import com.anysoftkeyboard.remote.gif.config.GifSourceType;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AnySoftKeyboardRobolectricTestRunner.class)
public class GiphyGifSourceTest {

  private static final String SEARCH_JSON =
      "{\"data\":[{\"id\":\"abc\",\"images\":{"
          + "\"fixed_width\":{\"url\":\"https://media.giphy.com/fw.gif\",\"width\":\"200\",\"height\":\"150\"},"
          + "\"original\":{\"url\":\"https://media.giphy.com/orig.gif\",\"width\":\"480\",\"height\":\"270\"}"
          + "}}]}";

  private static GifSourceConfig config(String apiKey) {
    return new GifSourceConfig("giphy-1", GifSourceType.GIPHY, "GIPHY", true, "", apiKey, "", "");
  }

  @Test
  public void testSearchMapsImages() throws Exception {
    final FakeHttpTransport transport =
        new FakeHttpTransport(
            (method, url, headers, body) -> FakeHttpTransport.response(200, SEARCH_JSON));
    final GiphyGifSource source = new GiphyGifSource(config("key"), transport);

    final List<GifResult> results = source.search("cats", 25);

    Assert.assertEquals(1, results.size());
    final GifResult gif = results.get(0);
    Assert.assertEquals("abc", gif.getId());
    Assert.assertEquals("https://media.giphy.com/fw.gif", gif.getPreviewUrl());
    Assert.assertEquals("https://media.giphy.com/orig.gif", gif.getFullGifUrl());
    Assert.assertEquals(480, gif.getWidth());
    Assert.assertEquals("giphy-1", gif.getSourceId());
    // The request carried the api key and query.
    Assert.assertTrue(transport.requestedUrls.get(0).contains("api_key=key"));
    Assert.assertTrue(transport.requestedUrls.get(0).contains("q=cats"));
  }

  @Test
  public void testEmptyApiKeyShortCircuits() throws Exception {
    final FakeHttpTransport transport =
        new FakeHttpTransport(
            (method, url, headers, body) -> {
              throw new AssertionError("should not hit the network without an api key");
            });
    final GiphyGifSource source = new GiphyGifSource(config(""), transport);

    Assert.assertTrue(source.search("cats", 25).isEmpty());
    Assert.assertTrue(transport.requestedUrls.isEmpty());
  }

  @Test(expected = java.io.IOException.class)
  public void testNonSuccessThrows() throws Exception {
    final FakeHttpTransport transport =
        new FakeHttpTransport((method, url, headers, body) -> FakeHttpTransport.response(500, ""));
    new GiphyGifSource(config("key"), transport).search("cats", 25);
  }
}
