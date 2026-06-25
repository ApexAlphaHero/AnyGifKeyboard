package com.anysoftkeyboard.ui.settings.gifsources;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfig;
import com.anysoftkeyboard.remote.gif.config.GifSourceConfigStore;
import com.anysoftkeyboard.remote.gif.config.GifSourceType;
import com.menny.android.anysoftkeyboard.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.evendanan.pixel.UiUtils;

/**
 * Settings screen listing the user's GIF sources. The list is reorderable (drag handle → priority),
 * each row has an enable switch, and tapping a row (or the add button) opens an editor dialog.
 * Backed by {@link GifSourceConfigStore}; the same store the search runtime reads.
 */
public class GifSourcesFragment extends Fragment implements GifSourcesAdapter.Listener {

  private final List<GifSourceConfig> mSources = new ArrayList<>();
  private GifSourceConfigStore mStore;
  private GifSourcesAdapter mAdapter;
  private ItemTouchHelper mItemTouchHelper;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.gif_sources_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    mStore = GifSourceConfigStore.create(requireContext());

    final RecyclerView recyclerView = view.findViewById(R.id.gif_sources_list);
    recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    mAdapter = new GifSourcesAdapter(mSources, this);
    recyclerView.setAdapter(mAdapter);

    mItemTouchHelper = new ItemTouchHelper(createReorderCallback());
    mItemTouchHelper.attachToRecyclerView(recyclerView);

    view.findViewById(R.id.gif_sources_add).setOnClickListener(v -> showEditDialog(null));
  }

  @Override
  public void onStart() {
    super.onStart();
    UiUtils.setActivityTitle(this, R.string.gif_sources_title);
    reload();
  }

  private void reload() {
    mSources.clear();
    mSources.addAll(mStore.getAll());
    mAdapter.notifyDataSetChanged();
  }

  @NonNull
  private ItemTouchHelper.Callback createReorderCallback() {
    return new ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0 /* no swipe */) {
      @Override
      public boolean onMove(
          @NonNull RecyclerView recyclerView,
          @NonNull RecyclerView.ViewHolder viewHolder,
          @NonNull RecyclerView.ViewHolder target) {
        final int from = viewHolder.getBindingAdapterPosition();
        final int to = target.getBindingAdapterPosition();
        Collections.swap(mSources, from, to);
        mAdapter.notifyItemMoved(from, to);
        mStore.save(mSources);
        return true;
      }

      @Override
      public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

      @Override
      public boolean isLongPressDragEnabled() {
        return false; // dragging starts from the handle only
      }
    };
  }

  @Override
  public void onSourceClicked(@NonNull GifSourceConfig config) {
    showEditDialog(config);
  }

  @Override
  public void onSourceEnabledChanged(@NonNull GifSourceConfig config, boolean enabled) {
    mStore.setEnabled(config.getId(), enabled);
    reload();
  }

  @Override
  public void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder) {
    mItemTouchHelper.startDrag(viewHolder);
  }

  private void showEditDialog(@Nullable GifSourceConfig existing) {
    final View form =
        LayoutInflater.from(requireContext()).inflate(R.layout.gif_source_edit_dialog, null);
    final EditText labelView = form.findViewById(R.id.gif_source_edit_label);
    final RadioGroup typeGroup = form.findViewById(R.id.gif_source_edit_type_group);
    final View gipheryGroup = form.findViewById(R.id.gif_source_edit_giphery_group);
    final View giphyGroup = form.findViewById(R.id.gif_source_edit_giphy_group);
    final EditText baseUrlView = form.findViewById(R.id.gif_source_edit_base_url);
    final EditText accessTokenView = form.findViewById(R.id.gif_source_edit_access_token);
    final EditText refreshTokenView = form.findViewById(R.id.gif_source_edit_refresh_token);
    final EditText apiKeyView = form.findViewById(R.id.gif_source_edit_api_key);

    final Runnable applyTypeVisibility =
        () -> {
          final boolean giphery =
              typeGroup.getCheckedRadioButtonId() == R.id.gif_source_type_giphery;
          gipheryGroup.setVisibility(giphery ? View.VISIBLE : View.GONE);
          giphyGroup.setVisibility(giphery ? View.GONE : View.VISIBLE);
        };
    typeGroup.setOnCheckedChangeListener((group, checkedId) -> applyTypeVisibility.run());

    if (existing != null) {
      labelView.setText(existing.getLabel());
      typeGroup.check(
          existing.getType() == GifSourceType.GIPHERY
              ? R.id.gif_source_type_giphery
              : R.id.gif_source_type_giphy);
      baseUrlView.setText(existing.getBaseUrl());
      accessTokenView.setText(existing.getAccessToken());
      refreshTokenView.setText(existing.getRefreshToken());
      apiKeyView.setText(existing.getApiKey());
      // Type is fixed once created to avoid orphaning the type-specific fields.
      for (int i = 0; i < typeGroup.getChildCount(); i++) {
        typeGroup.getChildAt(i).setEnabled(false);
      }
    } else {
      typeGroup.check(R.id.gif_source_type_giphery);
    }
    applyTypeVisibility.run();

    final AlertDialog.Builder builder =
        new AlertDialog.Builder(requireContext())
            .setTitle(
                existing == null ? R.string.gif_source_add_title : R.string.gif_source_edit_title)
            .setView(form)
            .setPositiveButton(
                R.string.gif_source_save,
                (dialog, which) ->
                    saveFromForm(
                        existing,
                        typeGroup.getCheckedRadioButtonId(),
                        labelView.getText().toString().trim(),
                        baseUrlView.getText().toString().trim(),
                        accessTokenView.getText().toString().trim(),
                        refreshTokenView.getText().toString().trim(),
                        apiKeyView.getText().toString().trim()))
            .setNegativeButton(android.R.string.cancel, null);

    if (existing != null) {
      builder.setNeutralButton(
          R.string.gif_source_delete,
          (dialog, which) -> {
            mStore.delete(existing.getId());
            reload();
          });
    }

    builder.show();
  }

  private void saveFromForm(
      @Nullable GifSourceConfig existing,
      int checkedTypeId,
      @NonNull String label,
      @NonNull String baseUrl,
      @NonNull String accessToken,
      @NonNull String refreshToken,
      @NonNull String apiKey) {
    final GifSourceType type =
        checkedTypeId == R.id.gif_source_type_giphery ? GifSourceType.GIPHERY : GifSourceType.GIPHY;
    final String id = existing != null ? existing.getId() : UUID.randomUUID().toString();
    final String resolvedLabel = label.isEmpty() ? type.name() : label;
    final boolean enabled = existing == null || existing.isEnabled();

    final GifSourceConfig config =
        new GifSourceConfig(
            id, type, resolvedLabel, enabled, baseUrl, apiKey, accessToken, refreshToken);
    mStore.upsert(config);
    reload();
  }
}
