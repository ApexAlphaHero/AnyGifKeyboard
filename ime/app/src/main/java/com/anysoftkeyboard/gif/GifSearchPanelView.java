package com.anysoftkeyboard.gif;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.anysoftkeyboard.base.utils.Logger;
import com.anysoftkeyboard.remote.gif.GifCache;
import com.anysoftkeyboard.remote.gif.GifRepository;
import com.anysoftkeyboard.remote.gif.GifResult;
import com.anysoftkeyboard.remote.gif.GifSource;
import com.anysoftkeyboard.remote.gif.GifSourceFactory;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfigStore;
import com.anysoftkeyboard.remote.gif.net.UrlConnectionHttpTransport;
import com.anysoftkeyboard.remote.gif.ui.GifImageLoader;
import com.anysoftkeyboard.remote.gif.ui.GifResultsAdapter;
import com.anysoftkeyboard.rx.RxSchedulers;
import com.menny.android.anysoftkeyboard.R;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import java.util.Collections;
import java.util.List;

/**
 * In-keyboard GIF search panel. Docked into the keyboard container (replacing the keyboard view,
 * like the emoji panel), it searches the user's configured sources and, on tap, downloads the GIF
 * and hands a local {@code content://} Uri back to the keyboard service to commit into the editor.
 *
 * <p>Unlike the rest of the keyboard, this panel does network I/O — but always off the main thread.
 */
public class GifSearchPanelView extends LinearLayout {
  private static final String TAG = "GifSearchPanelView";
  private static final int SEARCH_LIMIT = 30;

  /** Keyboard-service callbacks. */
  public interface Listener {
    void onGifChosen(@NonNull Uri localUri, @NonNull String[] mimeTypes);

    void onCloseGifPanel();

    /** The search box was tapped; the service should show the keyboard for typing a query. */
    void onGifSearchInputRequested();

    /** The emoji tab was tapped; the service should switch to the emoji/quick-text panel. */
    void onSwitchToEmojiPanel();
  }

  private EditText mSearchBox;
  private TextView mMessageView;
  private GifResultsAdapter mAdapter;

  private GifRepository mRepository;
  @Nullable private Listener mListener;
  private String[] mMimeTypes = new String[] {"image/gif"};

  private final CompositeDisposable mPreviewDisposables = new CompositeDisposable();
  private Disposable mSearchDisposable = Disposables.empty();
  private Disposable mDownloadDisposable = Disposables.empty();
  private boolean mInsertingGif = false;

  public GifSearchPanelView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    mMessageView = findViewById(R.id.gif_search_message);
    mSearchBox = findViewById(R.id.gif_search_box);

    final RecyclerView recyclerView = findViewById(R.id.gif_search_results);
    recyclerView.setLayoutManager(
        new StaggeredGridLayoutManager(
            getResources().getInteger(R.integer.gif_grid_columns),
            StaggeredGridLayoutManager.VERTICAL));
    mAdapter = new GifResultsAdapter(this::loadPreview, this::onGifClicked);
    recyclerView.setAdapter(mAdapter);

    // The search box can't host a soft keyboard inside the IME panel, so tapping it asks the
    // service to show the keyboard in a "search input" mode that routes typing here.
    mSearchBox.setFocusable(false);
    mSearchBox.setOnClickListener(
        v -> {
          if (mListener != null) mListener.onGifSearchInputRequested();
        });

    findViewById(R.id.gif_panel_close)
        .setOnClickListener(
            v -> {
              if (mListener != null) mListener.onCloseGifPanel();
            });
    findViewById(R.id.gif_panel_tab_emoji)
        .setOnClickListener(
            v -> {
              if (mListener != null) mListener.onSwitchToEmojiPanel();
            });

    setupQuickSearchChips();
  }

  /** Wires the panel up to the keyboard service and the current field's accepted mime types. */
  public void initialize(@NonNull Listener listener, @NonNull String[] mimeTypes) {
    mListener = listener;
    if (mimeTypes.length > 0) mMimeTypes = mimeTypes;

    final GifSourceConfigStore configStore = GifSourceConfigStore.create(getAppContext());
    mRepository =
        GifRepository.fromStore(
            configStore, new GifSourceFactory(new UrlConnectionHttpTransport(), configStore));

    showMessage(
        mRepository.hasSources()
            ? getResources().getString(R.string.gif_search_prompt)
            : getResources().getString(R.string.gif_search_no_sources));
  }

  private void setupQuickSearchChips() {
    final LinearLayout chipContainer = findViewById(R.id.gif_search_chips);
    final LayoutInflater inflater = LayoutInflater.from(getContext());
    for (String term : getResources().getStringArray(R.array.gif_quick_searches)) {
      final TextView chip =
          (TextView) inflater.inflate(R.layout.gif_search_chip, chipContainer, false);
      chip.setText(term);
      chip.setOnClickListener(
          v -> {
            mSearchBox.setText(term);
            mSearchBox.setSelection(term.length());
            doSearch(term);
          });
      chipContainer.addView(chip);
    }
  }

  /** Runs a search for {@code query} and reflects it in the search box (used by the input bar). */
  public void runSearch(@NonNull String query) {
    mSearchBox.setText(query);
    doSearch(query);
  }

  private void doSearch(@NonNull String query) {
    if (query.isEmpty() || mRepository == null || !mRepository.hasSources()) return;
    mSearchDisposable.dispose();
    mPreviewDisposables.clear();
    showMessage(getResources().getString(R.string.gif_search_searching));

    mSearchDisposable =
        Single.fromCallable(() -> mRepository.search(query, SEARCH_LIMIT))
            .subscribeOn(RxSchedulers.background())
            .observeOn(RxSchedulers.mainThread())
            .subscribe(this::onSearchResults, this::onSearchError);
  }

  private void onSearchResults(@NonNull List<GifResult> results) {
    mAdapter.setResults(results);
    if (results.isEmpty()) {
      showMessage(getResources().getString(R.string.gif_search_no_results));
    } else {
      hideMessage();
    }
  }

  private void onSearchError(@NonNull Throwable throwable) {
    Logger.w(TAG, throwable, "GIF search failed");
    mAdapter.setResults(Collections.emptyList());
    showMessage(getResources().getString(R.string.gif_search_error));
  }

  private void loadPreview(@NonNull GifResult result, @NonNull ImageView into) {
    if (mRepository == null) return;
    final GifSource source = mRepository.sourceFor(result);
    if (source == null) return;
    final Object boundId = result.getId();
    final Disposable disposable =
        Single.fromCallable(
                () -> GifImageLoader.decode(getResources(), source.downloadPreview(result)))
            .subscribeOn(RxSchedulers.background())
            .observeOn(RxSchedulers.mainThread())
            .subscribe(
                drawable -> {
                  if (boundId.equals(into.getTag(R.id.gif_result_id_tag))) {
                    into.setImageDrawable(drawable);
                    GifImageLoader.startIfAnimated(drawable);
                  }
                },
                throwable -> Logger.d(TAG, "Preview load failed: %s", throwable.getMessage()));
    mPreviewDisposables.add(disposable);
  }

  private void onGifClicked(@NonNull GifResult result) {
    if (mInsertingGif || mRepository == null) return;
    final GifSource source = mRepository.sourceFor(result);
    if (source == null) return;
    mInsertingGif = true;
    showMessage(getResources().getString(R.string.gif_search_inserting));

    mDownloadDisposable =
        Single.fromCallable(() -> GifCache.save(getAppContext(), source.downloadFullGif(result), result.getId()))
            .subscribeOn(RxSchedulers.background())
            .observeOn(RxSchedulers.mainThread())
            .subscribe(this::onGifReady, this::onDownloadError);
  }

  private void onGifReady(@NonNull Uri localUri) {
    mInsertingGif = false;
    if (mListener != null) mListener.onGifChosen(localUri, mMimeTypes);
  }

  private void onDownloadError(@NonNull Throwable throwable) {
    Logger.w(TAG, throwable, "Failed to download chosen GIF");
    mInsertingGif = false;
    showMessage(getResources().getString(R.string.gif_search_error));
  }

  private void showMessage(@NonNull String message) {
    mMessageView.setText(message);
    mMessageView.setVisibility(View.VISIBLE);
  }

  private void hideMessage() {
    mMessageView.setVisibility(View.GONE);
  }

  @NonNull
  private Context getAppContext() {
    return getContext().getApplicationContext();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    mSearchDisposable.dispose();
    mDownloadDisposable.dispose();
    mPreviewDisposables.clear();
  }
}
