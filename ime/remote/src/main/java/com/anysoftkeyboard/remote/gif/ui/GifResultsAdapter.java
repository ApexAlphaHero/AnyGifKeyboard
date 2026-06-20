package com.anysoftkeyboard.remote.gif.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.anysoftkeyboard.remote.R;
import com.anysoftkeyboard.remote.gif.GifResult;
import java.util.ArrayList;
import java.util.List;

/** Grid adapter for GIF search results. Preview loading is delegated to the host activity. */
public class GifResultsAdapter extends RecyclerView.Adapter<GifResultsAdapter.GifViewHolder> {

  /** Loads a result's preview into the given image view (host wires this to background I/O). */
  public interface PreviewLoader {
    void loadPreview(@NonNull GifResult result, @NonNull ImageView into);
  }

  /** Notified when the user taps a GIF to insert it. */
  public interface OnGifClickedListener {
    void onGifClicked(@NonNull GifResult result);
  }

  private final List<GifResult> mResults = new ArrayList<>();
  private final PreviewLoader mPreviewLoader;
  private final OnGifClickedListener mClickListener;

  public GifResultsAdapter(
      @NonNull PreviewLoader previewLoader, @NonNull OnGifClickedListener clickListener) {
    mPreviewLoader = previewLoader;
    mClickListener = clickListener;
  }

  public void setResults(@NonNull List<GifResult> results) {
    mResults.clear();
    mResults.addAll(results);
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public GifViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    final View itemView =
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.gif_search_result_item, parent, false);
    return new GifViewHolder(itemView);
  }

  @Override
  public void onBindViewHolder(@NonNull GifViewHolder holder, int position) {
    final GifResult result = mResults.get(position);
    holder.mImageView.setImageDrawable(null);
    // Tag guards against a recycled holder receiving a late preview from a previous bind.
    holder.mImageView.setTag(R.id.gif_result_id_tag, result.getId());
    holder.mImageView.setContentDescription(result.getId());
    holder.itemView.setOnClickListener(v -> mClickListener.onGifClicked(result));
    mPreviewLoader.loadPreview(result, holder.mImageView);
  }

  @Override
  public int getItemCount() {
    return mResults.size();
  }

  static class GifViewHolder extends RecyclerView.ViewHolder {
    private final ImageView mImageView;

    GifViewHolder(@NonNull View itemView) {
      super(itemView);
      mImageView = itemView.findViewById(R.id.gif_preview);
    }
  }
}
