package com.anysoftkeyboard.remote.gif.config;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.anysoftkeyboard.AnySoftKeyboardRobolectricTestRunner;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AnySoftKeyboardRobolectricTestRunner.class)
public class GifSourceConfigStoreTest {

  private GifSourceConfigStore mStore;

  @Before
  public void setup() {
    mStore =
        new GifSourceConfigStore(
            ApplicationProvider.<Context>getApplicationContext()
                .getSharedPreferences("gif_sources_test", Context.MODE_PRIVATE));
  }

  private static GifSourceConfig giphy(String id, String label, boolean enabled) {
    return new GifSourceConfig(id, GifSourceType.GIPHY, label, enabled, "", "key-" + id, "", "");
  }

  @Test
  public void testEmptyByDefault() {
    Assert.assertTrue(mStore.getAll().isEmpty());
    Assert.assertTrue(mStore.getEnabledInOrder().isEmpty());
  }

  @Test
  public void testSaveAndGetAllPreservesOrder() {
    mStore.save(Arrays.asList(giphy("a", "A", true), giphy("b", "B", true)));

    final List<GifSourceConfig> all = mStore.getAll();
    Assert.assertEquals(2, all.size());
    Assert.assertEquals("a", all.get(0).getId());
    Assert.assertEquals("b", all.get(1).getId());
    Assert.assertEquals("key-a", all.get(0).getApiKey());
  }

  @Test
  public void testUpsertAppendsNewAndReplacesExistingInPlace() {
    mStore.save(Arrays.asList(giphy("a", "A", true), giphy("b", "B", true)));

    mStore.upsert(giphy("c", "C", true));
    Assert.assertEquals(3, mStore.getAll().size());
    Assert.assertEquals("c", mStore.getAll().get(2).getId());

    mStore.upsert(giphy("a", "A-renamed", true));
    Assert.assertEquals(3, mStore.getAll().size());
    Assert.assertEquals("a", mStore.getAll().get(0).getId());
    Assert.assertEquals("A-renamed", mStore.getAll().get(0).getLabel());
  }

  @Test
  public void testDeleteRemovesById() {
    mStore.save(Arrays.asList(giphy("a", "A", true), giphy("b", "B", true)));
    mStore.delete("a");

    Assert.assertEquals(1, mStore.getAll().size());
    Assert.assertEquals("b", mStore.getAll().get(0).getId());
  }

  @Test
  public void testSetEnabledFlipsFlag() {
    mStore.save(Arrays.asList(giphy("a", "A", true)));
    mStore.setEnabled("a", false);

    Assert.assertFalse(mStore.get("a").isEnabled());
  }

  @Test
  public void testGetEnabledInOrderFiltersDisabledButKeepsOrder() {
    mStore.save(
        Arrays.asList(giphy("a", "A", true), giphy("b", "B", false), giphy("c", "C", true)));

    final List<GifSourceConfig> enabled = mStore.getEnabledInOrder();
    Assert.assertEquals(2, enabled.size());
    Assert.assertEquals("a", enabled.get(0).getId());
    Assert.assertEquals("c", enabled.get(1).getId());
  }
}
