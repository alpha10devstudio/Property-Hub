package com.propertyhub.offline.ui;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.propertyhub.offline.R;
import com.propertyhub.offline.model.ZipFileItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ZipFilesAdapter extends RecyclerView.Adapter<ZipFilesAdapter.ZipViewHolder> {

    public interface ZipActionListener {
        void onOpenZip(@NonNull ZipFileItem item);
        void onShareZip(@NonNull ZipFileItem item);
    }

    private final List<ZipFileItem> items = new ArrayList<>();
    private final ZipActionListener listener;

    public ZipFilesAdapter(@NonNull ZipActionListener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<ZipFileItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ZipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_zip_file, parent, false);
        return new ZipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ZipViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ZipViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvMeta;
        private final Button btnOpen;
        private final Button btnShare;

        ZipViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvZipFileName);
            tvMeta = itemView.findViewById(R.id.tvZipMeta);
            btnOpen = itemView.findViewById(R.id.btnZipOpen);
            btnShare = itemView.findViewById(R.id.btnZipShare);
        }

        void bind(@NonNull ZipFileItem item, @NonNull ZipActionListener listener) {
            tvName.setText(item.getFileName());
            String sizeStr = Formatter.formatShortFileSize(itemView.getContext(), item.getSizeBytes());
            String dateStr = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US)
                    .format(new Date(item.getLastModifiedMillis()));
            tvMeta.setText(sizeStr + " · " + dateStr);

            btnOpen.setOnClickListener(v -> listener.onOpenZip(item));
            btnShare.setOnClickListener(v -> listener.onShareZip(item));
        }
    }
}
