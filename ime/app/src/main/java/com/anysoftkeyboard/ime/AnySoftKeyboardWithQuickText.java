package com.anysoftkeyboard.ime;

import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.view.inputmethod.EditorInfoCompat;
import com.anysoftkeyboard.api.KeyCodes;
import com.anysoftkeyboard.gif.GifSearchPanelView;
import com.anysoftkeyboard.keyboards.Keyboard;
import com.anysoftkeyboard.keyboards.views.AnyKeyboardView;
import com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView;
import com.anysoftkeyboard.quicktextkeys.QuickTextKey;
import com.anysoftkeyboard.quicktextkeys.ui.DefaultGenderPrefTracker;
import com.anysoftkeyboard.quicktextkeys.ui.DefaultSkinTonePrefTracker;
import com.anysoftkeyboard.quicktextkeys.ui.QuickTextPagerView;
import com.anysoftkeyboard.quicktextkeys.ui.QuickTextViewFactory;
import com.anysoftkeyboard.rx.GenericOnError;
import com.menny.android.anysoftkeyboard.AnyApplication;
import com.menny.android.anysoftkeyboard.R;

public abstract class AnySoftKeyboardWithQuickText extends AnySoftKeyboardMediaInsertion {

  private boolean mDoNotFlipQuickTextKeyAndPopupFunctionality;
  private String mOverrideQuickTextText = "";
  private DefaultSkinTonePrefTracker mDefaultSkinTonePrefTracker;
  private DefaultGenderPrefTracker mDefaultGenderPrefTracker;

  private GifSearchPanelView mActiveGifPanel;
  private View mGifSearchInputBar;
  private TextView mGifSearchQueryView;
  private boolean mGifSearchInputActive;
  private final StringBuilder mGifSearchQuery = new StringBuilder();

  @Override
  public void onCreate() {
    super.onCreate();
    addDisposable(
        prefs()
            .getBoolean(
                R.string.settings_key_do_not_flip_quick_key_codes_functionality,
                R.bool.settings_default_do_not_flip_quick_keys_functionality)
            .asObservable()
            .subscribe(
                value -> mDoNotFlipQuickTextKeyAndPopupFunctionality = value,
                GenericOnError.onError("settings_key_do_not_flip_quick_key_codes_functionality")));

    addDisposable(
        prefs()
            .getString(R.string.settings_key_emoticon_default_text, R.string.settings_default_empty)
            .asObservable()
            .subscribe(
                value -> mOverrideQuickTextText = value,
                GenericOnError.onError("settings_key_emoticon_default_text")));

    mDefaultSkinTonePrefTracker = new DefaultSkinTonePrefTracker(prefs());
    addDisposable(mDefaultSkinTonePrefTracker);
    mDefaultGenderPrefTracker = new DefaultGenderPrefTracker(prefs());
    addDisposable(mDefaultGenderPrefTracker);
  }

  protected void onQuickTextRequested(Keyboard.Key key) {
    if (mDoNotFlipQuickTextKeyAndPopupFunctionality) {
      outputCurrentQuickTextKey(key);
    } else {
      switchToQuickTextKeyboard();
    }
  }

  protected void onQuickTextKeyboardRequested(Keyboard.Key key) {
    if (mDoNotFlipQuickTextKeyAndPopupFunctionality) {
      switchToQuickTextKeyboard();
    } else {
      outputCurrentQuickTextKey(key);
    }
  }

  private void outputCurrentQuickTextKey(Keyboard.Key key) {
    QuickTextKey quickTextKey = AnyApplication.getQuickTextKeyFactory(this).getEnabledAddOn();
    if (TextUtils.isEmpty(mOverrideQuickTextText)) {
      final CharSequence keyOutputText = quickTextKey.getKeyOutputText();
      onText(key, keyOutputText);
    } else {
      onText(key, mOverrideQuickTextText);
    }
  }

  @Override
  public void onFinishInputView(boolean finishingInput) {
    super.onFinishInputView(finishingInput);
    cleanUpQuickTextKeyboard(true);
    cleanUpGifSearchInputBar();
    cleanUpGifPanel(false);
  }

  /** Show the in-keyboard GIF search panel instead of delegating to the remote media picker. */
  @Override
  protected void handleMediaInsertionKey() {
    showGifSearchPanel(null);
  }

  private void showGifSearchPanel(@Nullable String initialQuery) {
    final KeyboardViewContainerView inputViewContainer = getInputViewContainer();
    if (inputViewContainer == null) return;
    abortCorrectionAndResetPredictionState(false);
    cleanUpQuickTextKeyboard(false);
    cleanUpGifSearchInputBar();

    final AnyKeyboardView actualInputView = (AnyKeyboardView) getInputView();
    final GifSearchPanelView panel =
        (GifSearchPanelView)
            LayoutInflater.from(inputViewContainer.getContext())
                .inflate(R.layout.gif_search_panel, inputViewContainer, false);

    final String[] mimeTypes = EditorInfoCompat.getContentMimeTypes(getCurrentInputEditorInfo());
    panel.initialize(
        new GifSearchPanelView.Listener() {
          @Override
          public void onGifChosen(Uri localUri, String[] mimes) {
            commitGifContent(localUri, mimes);
            cleanUpGifPanel(true);
          }

          @Override
          public void onCloseGifPanel() {
            cleanUpGifPanel(true);
          }

          @Override
          public void onGifSearchInputRequested() {
            enterGifSearchInputMode();
          }

          @Override
          public void onSwitchToEmojiPanel() {
            cleanUpGifPanel(false);
            switchToQuickTextKeyboard();
          }
        },
        mimeTypes);

    if (actualInputView != null) {
      final int keyboardHeight = actualInputView.getHeight();
      // Keep the bottom tab strip above the system navigation bar (same inset the keyboard uses).
      final int bottomInset = actualInputView.getPaddingBottom();
      if (keyboardHeight > 0 && panel.getLayoutParams() != null) {
        // Make the GIF panel taller than the keyboard (Gboard-style) so more rows are visible,
        // capped so it never takes more than ~62% of the screen height.
        final int screenHeight = getResources().getDisplayMetrics().heightPixels;
        panel.getLayoutParams().height =
            Math.min((int) (keyboardHeight * 1.6f), (int) (screenHeight * 0.62f)) + bottomInset;
      }
      panel.setPadding(
          panel.getPaddingLeft(),
          panel.getPaddingTop(),
          panel.getPaddingRight(),
          panel.getPaddingBottom() + bottomInset);
      if (actualInputView.getBackground() != null) {
        panel.setBackground(actualInputView.getBackground());
      }
      actualInputView.resetInputView();
      actualInputView.setVisibility(View.GONE);
    }
    inputViewContainer.addView(panel);
    mActiveGifPanel = panel;
    if (initialQuery != null && !initialQuery.isEmpty()) {
      panel.runSearch(initialQuery);
    }
  }

  private boolean cleanUpGifPanel(boolean reshowStandardKeyboard) {
    final KeyboardViewContainerView inputViewContainer = getInputViewContainer();
    if (inputViewContainer == null) return false;

    if (reshowStandardKeyboard) {
      View standardKeyboardView = (View) getInputView();
      if (standardKeyboardView != null) {
        standardKeyboardView.setVisibility(View.VISIBLE);
      }
    }

    mActiveGifPanel = null;
    View gifPanel = inputViewContainer.findViewById(R.id.gif_search_panel_root);
    if (gifPanel != null) {
      inputViewContainer.removeView(gifPanel);
      return true;
    }
    return false;
  }

  // --- GIF search text-input mode: show the keyboard + a search bar and route typing to the query.

  /** Swap the grid panel for the keyboard plus a slim search bar, and start capturing typing. */
  private void enterGifSearchInputMode() {
    final KeyboardViewContainerView inputViewContainer = getInputViewContainer();
    if (inputViewContainer == null) return;

    cleanUpGifPanel(true); // remove the grid panel and re-show the keyboard
    cleanUpGifSearchInputBar();

    final View keyboardView = (View) getInputView();
    final View bar =
        LayoutInflater.from(inputViewContainer.getContext())
            .inflate(R.layout.gif_search_input_bar, inputViewContainer, false);
    mGifSearchQueryView = bar.findViewById(R.id.gif_search_input_query);
    bar.findViewById(R.id.gif_search_input_back).setOnClickListener(v -> exitGifSearchInputMode());

    // Place the search bar directly above the keyboard (the container stacks children vertically).
    final int keyboardIndex = inputViewContainer.indexOfChild(keyboardView);
    if (keyboardIndex >= 0) {
      inputViewContainer.addView(bar, keyboardIndex);
    } else {
      inputViewContainer.addView(bar);
    }
    mGifSearchInputBar = bar;
    mGifSearchInputActive = true;
    mGifSearchQuery.setLength(0);
    updateGifSearchQueryView();
  }

  /** Routes a key press to the GIF search query while input mode is active; true if consumed. */
  protected boolean handleGifSearchInputKey(int primaryCode) {
    if (!mGifSearchInputActive) return false;
    switch (primaryCode) {
      case KeyCodes.ENTER:
        runGifSearchFromInput();
        return true;
      case KeyCodes.CANCEL:
        exitGifSearchInputMode();
        return true;
      case KeyCodes.DELETE:
        if (mGifSearchQuery.length() > 0) {
          mGifSearchQuery.deleteCharAt(mGifSearchQuery.length() - 1);
          updateGifSearchQueryView();
        }
        return true;
      default:
        if (primaryCode > 0 && !Character.isISOControl(primaryCode)) {
          mGifSearchQuery.appendCodePoint(primaryCode);
          updateGifSearchQueryView();
          return true;
        }
        // Let function keys (shift, symbols, language switch) work on the keyboard normally.
        return false;
    }
  }

  private void updateGifSearchQueryView() {
    if (mGifSearchQueryView != null) {
      mGifSearchQueryView.setText(mGifSearchQuery.toString());
    }
  }

  private void runGifSearchFromInput() {
    final String query = mGifSearchQuery.toString().trim();
    cleanUpGifSearchInputBar();
    showGifSearchPanel(query.isEmpty() ? null : query);
  }

  private void exitGifSearchInputMode() {
    cleanUpGifSearchInputBar();
    showGifSearchPanel(null);
  }

  private void cleanUpGifSearchInputBar() {
    mGifSearchInputActive = false;
    mGifSearchQueryView = null;
    final KeyboardViewContainerView inputViewContainer = getInputViewContainer();
    if (inputViewContainer == null) {
      mGifSearchInputBar = null;
      return;
    }
    final View bar =
        mGifSearchInputBar != null
            ? mGifSearchInputBar
            : inputViewContainer.findViewById(R.id.gif_search_input_bar_root);
    if (bar != null && bar.getParent() == inputViewContainer) {
      inputViewContainer.removeView(bar);
    }
    mGifSearchInputBar = null;
  }

  private void switchToQuickTextKeyboard() {
    final KeyboardViewContainerView inputViewContainer = getInputViewContainer();
    abortCorrectionAndResetPredictionState(false);

    cleanUpQuickTextKeyboard(false);

    final AnyKeyboardView actualInputView = (AnyKeyboardView) getInputView();
    QuickTextPagerView quickTextsLayout =
        QuickTextViewFactory.createQuickTextView(
            getApplicationContext(),
            inputViewContainer,
            getQuickKeyHistoryRecords(),
            mDefaultSkinTonePrefTracker,
            mDefaultGenderPrefTracker);
    actualInputView.resetInputView();
    quickTextsLayout.setThemeValues(
        mCurrentTheme,
        actualInputView.getLabelTextSize(),
        actualInputView.getCurrentResourcesHolder().getKeyTextColor(),
        actualInputView.getDrawableForKeyCode(KeyCodes.CANCEL),
        actualInputView.getDrawableForKeyCode(KeyCodes.DELETE),
        actualInputView.getDrawableForKeyCode(KeyCodes.SETTINGS),
        actualInputView.getBackground(),
        actualInputView.getDrawableForKeyCode(KeyCodes.CLEAR_QUICK_TEXT_HISTORY),
        actualInputView.getPaddingBottom(),
        getSupportedMediaTypesForInput());

    actualInputView.setVisibility(View.GONE);
    inputViewContainer.addView(quickTextsLayout);
  }

  private boolean cleanUpQuickTextKeyboard(boolean reshowStandardKeyboard) {
    final KeyboardViewContainerView inputViewContainer = getInputViewContainer();
    if (inputViewContainer == null) return false;

    if (reshowStandardKeyboard) {
      View standardKeyboardView = (View) getInputView();
      if (standardKeyboardView != null) {
        standardKeyboardView.setVisibility(View.VISIBLE);
      }
    }

    QuickTextPagerView quickTextsLayout =
        inputViewContainer.findViewById(R.id.quick_text_pager_root);
    if (quickTextsLayout != null) {
      inputViewContainer.removeView(quickTextsLayout);
      return true;
    } else {
      return false;
    }
  }

  @Override
  protected boolean handleCloseRequest() {
    if (mGifSearchInputActive) {
      exitGifSearchInputMode();
      return true;
    }
    return super.handleCloseRequest()
        || cleanUpQuickTextKeyboard(true)
        || cleanUpGifPanel(true);
  }
}
