package com.anysoftkeyboard.remote.gif.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.anysoftkeyboard.base.utils.Logger;
import com.anysoftkeyboard.remote.R;
import com.anysoftkeyboard.remote.gif.GifRepository;
import com.anysoftkeyboard.remote.gif.GifResult;
import com.anysoftkeyboard.remote.gif.GifSource;
import com.anysoftkeyboard.remote.gif.GifSourceFactory;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfigStore;
import com.anysoftkeyboard.remote.gif.net.UrlConnectionHttpTransport;
import com.anysoftkeyboard.rx.RxSchedulers;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * In-keyboard GIF search screen. Queries the user's configured sources (via {@link GifRepository}),
 * shows results in a grid, and — when one is tapped — downloads it to an app-private file and
 * returns a {@code content://} Uri to {@code RemoteInsertionActivity}, which feeds it into the
 * keyboard's existing media-insertion pipeline.
 */
public class GifSearchActivity extends FragmentActivity {
  private static final String TAG = "GifSearchActivity";
  private static final int SEARCH_LIMIT = 30;

  private GifRepository mRepository;
  private GifResultsAdapter mAdapter;
  private TextView mMessageView;

  private final CompositeDisposable mPreviewDisposables = new CompositeDisposable();
  private Disposable mSearchDisposable = Disposables.empty();
  private Disposable mDownloadDisposable = Disposables.empty();
  private boolean mInsertingGif = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.gif_search_activity);

    final GifSourceConfigStore configStore = GifSourceConfigStore.create(this);
    mRepository =
        GifRepository.fromStore(
            configStore, new GifSourceFactory(new UrlConnectionHttpTransport(), configStore));

    mMessageView = findViewById(R.id.gif_search_message);
    final RecyclerView recyclerView = findViewById(R.id.gif_search_results);
    recyclerView.setLayoutManager(
        new GridLayoutManager(this, getResources().getInteger(R.integer.gif_grid_columns)));
    mAdapter = new GifResultsAdapter(this::loadPreview, this::onGifClicked);
    recyclerView.setAdapter(mAdapter);

    final EditText searchBox = findViewById(R.id.gif_search_box);
    searchBox.setOnEditorActionListener(
        (v, actionId, event) -> {
          if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            doSearch(v.getText().toString().trim());
            return true;
          }
          return false;
        });

    if (!mRepository.hasSources()) {
      showMessage(getString(R.string.gif_search_no_sources));
    } else {
      showMessage(getString(R.string.gif_search_prompt));
    }
  }

  private void doSearch(@NonNull String query) {
    if (query.isEmpty() || !mRepository.hasSources()) return;
    mSearchDisposable.dispose();
    mPreviewDisposables.clear();
    showMessage(getString(R.string.gif_search_searching));

    mSearchDisposable =
        Single.fromCallable(() -> mRepository.search(query, SEARCH_LIMIT))
            .subscribeOn(RxSchedulers.background())
            .observeOn(RxSchedulers.mainThread())
            .subscribe(this::onSearchResults, this::onSearchError);
  }

  private void onSearchResults(@NonNull List<GifResult> results) {
    mAdapter.setResults(results);
    if (results.isEmpty()) {
      showMessage(getString(R.string.gif_search_no_results));
    } else {
      hideMessage();
    }
  }

  private void onSearchError(@NonNull Throwable throwable) {
    Logger.w(TAG, throwable, "GIF search failed");
    mAdapter.setResults(Collections.emptyList());
    showMessage(getString(R.string.gif_search_error));
  }

  private void loadPreview(@NonNull GifResult result, @NonNull ImageView into) {
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
                  // Skip if this view was recycled to a different result in the meantime.
                  if (boundId.equals(into.getTag(R.id.gif_result_id_tag))) {
                    into.setImageDrawable(drawable);
                    GifImageLoader.startIfAnimated(drawable);
                  }
                },
                throwable -> Logger.d(TAG, "Preview load failed: %s", throwable.getMessage()));
    mPreviewDisposables.add(disposable);
  }

  private void onGifClicked(@NonNull GifResult result) {
    if (mInsertingGif) return;
    final GifSource source = mRepository.sourceFor(result);
    if (source == null) return;
    mInsertingGif = true;
    showMessage(getString(R.string.gif_search_inserting));

    mDownloadDisposable =
        Single.fromCallable(() -> downloadToLocalUri(source, result))
            .subscribeOn(RxSchedulers.background())
            .observeOn(RxSchedulers.mainThread())
            .subscribe(this::returnInsertedGif, this::onDownloadError);
  }

  private void onDownloadError(@NonNull Throwable throwable) {
    Logger.w(TAG, throwable, "Failed to download chosen GIF");
    mInsertingGif = false;
    hideMessage();
    showMessage(getString(R.string.gif_search_error));
  }

  @NonNull
  private Uri downloadToLocalUri(@NonNull GifSource source, @NonNull GifResult result)
      throws IOException {
    final byte[] bytes = source.downloadFullGif(result);
    final File mediaFolder = new File(getFilesDir(), "media");
    if (!mediaFolder.isDirectory() && !mediaFolder.mkdirs()) {
      throw new IOException("Could not create media folder");
    }
    final String safeName =
        String.format(Locale.ROOT, "gif_%s.gif", Integer.toHexString(result.getId().hashCode()));
    final File target = new File(mediaFolder, safeName);
    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
      out.write(bytes);
    }
    return FileProvider.getUriForFile(this, getPackageName(), target);
  }

  private void returnInsertedGif(@NonNull Uri uri) {
    final Intent result = new Intent();
    result.setData(uri);
    result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    setResult(Activity.RESULT_OK, result);
    finish();
  }

  private void showMessage(@NonNull String message) {
    mMessageView.setText(message);
    mMessageView.setVisibility(View.VISIBLE);
  }

  private void hideMessage() {
    mMessageView.setVisibility(View.GONE);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mSearchDisposable.dispose();
    mDownloadDisposable.dispose();
    mPreviewDisposables.clear();
  }
}
