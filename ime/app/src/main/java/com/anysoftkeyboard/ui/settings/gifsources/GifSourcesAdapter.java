package com.anysoftkeyboard.ui.settings.gifsources;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfig;
import com.menny.android.anysoftkeyboard.R;
import java.util.List;

/** Renders the user's GIF sources: label, type, an enable switch, and a drag handle for reorder. */
public class GifSourcesAdapter extends RecyclerView.Adapter<GifSourcesAdapter.SourceViewHolder> {

  /** Host callbacks for row interactions. */
  public interface Listener {
    void onSourceClicked(@NonNull GifSourceConfig config);

    void onSourceEnabledChanged(@NonNull GifSourceConfig config, boolean enabled);

    void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder);
  }

  private final List<GifSourceConfig> mSources;
  private final Listener mListener;

  public GifSourcesAdapter(@NonNull List<GifSourceConfig> sources, @NonNull Listener listener) {
    mSources = sources;
    mListener = listener;
  }

  @NonNull
  @Override
  public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    final View view =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.gif_source_item, parent, false);
    return new SourceViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
    holder.bind(mSources.get(position));
  }

  @Override
  public int getItemCount() {
    return mSources.size();
  }

  class SourceViewHolder extends RecyclerView.ViewHolder {
    private final TextView mLabel;
    private final TextView mSubtitle;
    private final SwitchCompat mEnabledSwitch;
    private final View mDragHandle;

    SourceViewHolder(@NonNull View itemView) {
      super(itemView);
      mLabel = itemView.findViewById(R.id.gif_source_label);
      mSubtitle = itemView.findViewById(R.id.gif_source_subtitle);
      mEnabledSwitch = itemView.findViewById(R.id.gif_source_enabled);
      mDragHandle = itemView.findViewById(R.id.gif_source_drag_handle);
    }

    @SuppressLint("ClickableViewAccessibility")
    void bind(@NonNull GifSourceConfig config) {
      mLabel.setText(config.getLabel());
      mSubtitle.setText(config.getType().name());

      // Detach the listener while setting the initial state so it doesn't fire on (re)bind.
      mEnabledSwitch.setOnCheckedChangeListener(null);
      mEnabledSwitch.setChecked(config.isEnabled());
      mEnabledSwitch.setOnCheckedChangeListener(
          (button, isChecked) -> mListener.onSourceEnabledChanged(config, isChecked));

      itemView.setOnClickListener(v -> mListener.onSourceClicked(config));
      mDragHandle.setOnTouchListener(
          (v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
              mListener.onStartDrag(this);
            }
            return false;
          });
    }
  }
}
