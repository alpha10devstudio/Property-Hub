package com.propertyhub.offline.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.propertyhub.offline.R;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;

import java.util.ArrayList;
import java.util.List;

public class RecordsAdapter extends RecyclerView.Adapter<RecordsAdapter.RecordViewHolder> {

    public interface OnRecordClickListener {
        void onRecordClicked(@NonNull PropertyRecord record);
    }

    private final List<PropertyRecord> items = new ArrayList<>();
    private final OnRecordClickListener listener;

    public RecordsAdapter(@NonNull OnRecordClickListener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<PropertyRecord> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_property_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        PropertyRecord item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class RecordViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvId;
        private final TextView tvDate;
        private final TextView tvMeta;
        private final TextView tvDetails;
        private final TextView tvStatus;

        RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvId = itemView.findViewById(R.id.tvRecordId);
            tvDate = itemView.findViewById(R.id.tvRecordDate);
            tvMeta = itemView.findViewById(R.id.tvRecordMeta);
            tvDetails = itemView.findViewById(R.id.tvRecordDetailsPreview);
            tvStatus = itemView.findViewById(R.id.tvRecordPosterStatus);
        }

        void bind(@NonNull PropertyRecord record, @NonNull OnRecordClickListener listener) {
            tvId.setText(record.getRecordId());
            tvDate.setText(record.getDate() + (record.getTime().isEmpty() ? "" : " · " + record.getTime()));
            tvMeta.setText(record.getCategory().getDisplayName() + " · " + record.getMobile());
            tvDetails.setText(record.getDetails().replace('\n', ' '));

            if (record.isPosterGenerated()) {
                tvStatus.setText("Poster: Generated (" + record.getPosterFileName() + ")");
                tvStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_primary));
            } else {
                tvStatus.setText("Poster: Not Generated");
                tvStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.warning_text));
            }

            if (record.getCategory() == PropertyCategory.HOUSE_FOR_RENT) {
                tvId.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.blue_primary));
            } else {
                tvId.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_primary));
            }

            itemView.setOnClickListener(v -> listener.onRecordClicked(record));
        }
    }
}
