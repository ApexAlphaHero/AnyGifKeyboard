package com.anysoftkeyboard.ime;

import android.net.Uri;
import android.view.inputmethod.EditorInfo;
import androidx.core.view.inputmethod.EditorInfoCompat;
import com.anysoftkeyboard.AnySoftKeyboardBaseTest;
import com.anysoftkeyboard.AnySoftKeyboardRobolectricTestRunner;
import com.anysoftkeyboard.api.KeyCodes;
import com.anysoftkeyboard.remote.MediaType;
import com.anysoftkeyboard.remote.RemoteInsertion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AnySoftKeyboardRobolectricTestRunner.class)
public class AnySoftKeyboardMediaInsertionTest extends AnySoftKeyboardBaseTest {

  private AnySoftKeyboardMediaInsertion mPackageScope;
  private RemoteInsertion mRemoteInsertion;

  @Before
  public void setup() {
    mPackageScope = mAnySoftKeyboardUnderTest;
    // it says 'createRemoteInsertion', but it actually returns a mock
    mRemoteInsertion = mPackageScope.createRemoteInsertion();
    Assert.assertTrue(Mockito.mockingDetails(mRemoteInsertion).isMock());
  }

  @Test
  public void testReportsMediaTypesAndClearsOnFinish() {
    simulateFinishInputFlow();
    EditorInfo info = createEditorInfoTextWithSuggestionsForSetUp();

    simulateOnStartInputFlow(false, info);
    Assert.assertTrue(mPackageScope.getSupportedMediaTypesForInput().isEmpty());
    simulateFinishInputFlow();
    Assert.assertTrue(mPackageScope.getSupportedMediaTypesForInput().isEmpty());

    EditorInfoCompat.setContentMimeTypes(info, new String[] {"image/jpg"});
    simulateOnStartInputFlow(false, info);
    Assert.assertTrue(mPackageScope.getSupportedMediaTypesForInput().contains(MediaType.Image));
    Assert.assertFalse(mPackageScope.getSupportedMediaTypesForInput().contains(MediaType.Gif));
    simulateFinishInputFlow();
    Assert.assertTrue(mPackageScope.getSupportedMediaTypesForInput().isEmpty());

    EditorInfoCompat.setContentMimeTypes(info, new String[] {"image/gif"});
    simulateOnStartInputFlow(false, info);
    Assert.assertTrue(mPackageScope.getSupportedMediaTypesForInput().contains(MediaType.Image));
    Assert.assertTrue(mPackageScope.getSupportedMediaTypesForInput().contains(MediaType.Gif));
    simulateFinishInputFlow();
    Assert.assertTrue(mPackageScope.getSupportedMediaTypesForInput().isEmpty());

    EditorInfoCompat.setContentMimeTypes(info, new String[] {"image/menny_image"});
    simulateOnStartInputFlow(false, info);
    Assert.assertTrue(mPackageScope.getSupportedMediaTypesForInput().contains(MediaType.Image));
    Assert.assertFalse(mPackageScope.getSupportedMediaTypesForInput().contains(MediaType.Gif));
    simulateFinishInputFlow();
    Assert.assertTrue(mPackageScope.getSupportedMediaTypesForInput().isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testMediaTypesIsUnmodifiable() {
    mPackageScope.getSupportedMediaTypesForInput().add(MediaType.Image);
  }

  @Test
  public void testMediaKeyOpensGifPanelInsteadOfRemoteInsertion() {
    simulateFinishInputFlow();
    EditorInfo info = createEditorInfoTextWithSuggestionsForSetUp();
    EditorInfoCompat.setContentMimeTypes(info, new String[] {"image/gif"});
    simulateOnStartInputFlow(false, info);

    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.IMAGE_MEDIA_POPUP);

    // The in-keyboard GIF search panel now handles the media key, so the remote
    // media picker is never invoked (see AnySoftKeyboardWithQuickText#handleMediaInsertionKey).
    Mockito.verify(mRemoteInsertion, Mockito.never())
        .startMediaRequest(Mockito.any(), Mockito.anyInt(), Mockito.any());
  }

  @Test
  public void testCommitGifContentCommitsToInputConnection() {
    simulateFinishInputFlow();
    EditorInfo info = createEditorInfoTextWithSuggestionsForSetUp();
    EditorInfoCompat.setContentMimeTypes(info, new String[] {"image/gif"});
    simulateOnStartInputFlow(false, info);

    Assert.assertNull(mAnySoftKeyboardUnderTest.getCommitedInputContentInfo());

    mPackageScope.commitGifContent(Uri.EMPTY, new String[] {"image/gif"});

    Assert.assertNotNull(mAnySoftKeyboardUnderTest.getCommitedInputContentInfo());
  }

  @Test
  public void testDoesNotCommitGifWithoutActiveInput() {
    simulateFinishInputFlow();

    // No active input connection/editor: commitGifContent must be a no-op.
    mPackageScope.commitGifContent(Uri.EMPTY, new String[] {"image/gif"});

    Assert.assertNull(mAnySoftKeyboardUnderTest.getCommitedInputContentInfo());
  }

  @Test
  public void testDestroyRemoteOnServiceDestroy() {
    Mockito.verify(mRemoteInsertion, Mockito.never()).destroy();

    mAnySoftKeyboardController.destroy();

    Mockito.verify(mRemoteInsertion).destroy();
  }
}
