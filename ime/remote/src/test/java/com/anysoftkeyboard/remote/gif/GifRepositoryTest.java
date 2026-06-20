package com.anysoftkeyboard.remote.gif;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class GifRepositoryTest {

  /** Fake source returning a fixed list, an empty list, or throwing. */
  private static class FakeGifSource implements GifSource {
    private final String mId;
    private final List<GifResult> mResults;
    private final boolean mThrow;
    int searchCount = 0;

    FakeGifSource(String id, List<GifResult> results, boolean doThrow) {
      mId = id;
      mResults = results;
      mThrow = doThrow;
    }

    @NonNull
    @Override
    public String id() {
      return mId;
    }

    @NonNull
    @Override
    public String label() {
      return mId;
    }

    @NonNull
    @Override
    public List<GifResult> search(@NonNull String query, int limit) throws IOException {
      searchCount++;
      if (mThrow) throw new IOException("boom");
      return mResults;
    }

    @NonNull
    @Override
    public byte[] download(@NonNull String url) {
      return new byte[0];
    }
  }

  private static GifResult result(String id, String sourceId) {
    return new GifResult(id, "p", "f", "image/gif", 1, 1, sourceId);
  }

  @Test
  public void testFirstSourceWithResultsWins() {
    final FakeGifSource first =
        new FakeGifSource("first", Collections.singletonList(result("g1", "first")), false);
    final FakeGifSource second =
        new FakeGifSource("second", Collections.singletonList(result("g2", "second")), false);
    final GifRepository repository = new GifRepository(Arrays.asList(first, second));

    final List<GifResult> results = repository.search("cats", 10);

    Assert.assertEquals(1, results.size());
    Assert.assertEquals("g1", results.get(0).getId());
    Assert.assertEquals(1, first.searchCount);
    Assert.assertEquals(0, second.searchCount); // never reached
  }

  @Test
  public void testEmptyFirstFallsThroughToSecond() {
    final FakeGifSource first = new FakeGifSource("first", new ArrayList<>(), false);
    final FakeGifSource second =
        new FakeGifSource("second", Collections.singletonList(result("g2", "second")), false);
    final GifRepository repository = new GifRepository(Arrays.asList(first, second));

    final List<GifResult> results = repository.search("cats", 10);

    Assert.assertEquals("g2", results.get(0).getId());
    Assert.assertEquals(1, first.searchCount);
    Assert.assertEquals(1, second.searchCount);
  }

  @Test
  public void testThrowingSourceIsSkipped() {
    final FakeGifSource first = new FakeGifSource("first", new ArrayList<>(), true);
    final FakeGifSource second =
        new FakeGifSource("second", Collections.singletonList(result("g2", "second")), false);
    final GifRepository repository = new GifRepository(Arrays.asList(first, second));

    final List<GifResult> results = repository.search("cats", 10);

    Assert.assertEquals("g2", results.get(0).getId());
  }

  @Test
  public void testAllEmptyReturnsEmpty() {
    final FakeGifSource first = new FakeGifSource("first", new ArrayList<>(), false);
    final FakeGifSource second = new FakeGifSource("second", new ArrayList<>(), false);
    final GifRepository repository = new GifRepository(Arrays.asList(first, second));

    Assert.assertTrue(repository.search("cats", 10).isEmpty());
  }

  @Test
  public void testNoSources() {
    final GifRepository repository = new GifRepository(Collections.emptyList());
    Assert.assertFalse(repository.hasSources());
    Assert.assertTrue(repository.search("cats", 10).isEmpty());
  }

  @Test
  public void testSourceForResolvesBySourceId() {
    final FakeGifSource first =
        new FakeGifSource("first", Collections.singletonList(result("g1", "first")), false);
    final GifRepository repository = new GifRepository(Collections.singletonList(first));

    Assert.assertSame(first, repository.sourceFor(result("g1", "first")));
    Assert.assertNull(repository.sourceFor(result("x", "missing")));
  }
}
