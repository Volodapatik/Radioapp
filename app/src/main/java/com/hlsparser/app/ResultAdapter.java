package com.hlsparser.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * ResultAdapter - displays found HLS streams in a RecyclerView.
 * Each item shows the URL, type, resolution, and action buttons.
 */
public class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ViewHolder> {

    private List<HLSAnalyzer.HLSStream> streams;
    private Context context;

    public interface OnStreamActionListener {
        void onCopy(HLSAnalyzer.HLSStream stream);
        void onSave(HLSAnalyzer.HLSStream stream);
        void onPlay(HLSAnalyzer.HLSStream stream);
    }

    private OnStreamActionListener listener;

    public ResultAdapter(Context context) {
        this.context = context;
        this.streams = new ArrayList<>();
    }

    public void setListener(OnStreamActionListener listener) {
        this.listener = listener;
    }

    public void setStreams(List<HLSAnalyzer.HLSStream> streams) {
        this.streams = streams;
        notifyDataSetChanged();
    }

    public void addStreams(List<HLSAnalyzer.HLSStream> newStreams) {
        int start = streams.size();
        streams.addAll(newStreams);
        notifyItemRangeInserted(start, newStreams.size());
    }

    public List<HLSAnalyzer.HLSStream> getStreams() {
        return streams;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        HLSAnalyzer.HLSStream stream = streams.get(position);

        // Set URL
        holder.tvUrl.setText(stream.getUrl());

        // Set meta info
        String typeLabel = getTypeLabel(stream.getType());
        holder.tvStreamType.setText(context.getString(R.string.stream_type) + " " + typeLabel);

        if (!stream.getResolution().isEmpty()) {
            holder.tvResolution.setText(context.getString(R.string.resolution) + " " + stream.getResolution());
            holder.metaContainer.setVisibility(View.VISIBLE);
        } else {
            holder.metaContainer.setVisibility(View.GONE);
        }

        // Copy button
        holder.btnCopyUrl.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCopy(stream);
            } else {
                copyToClipboard(stream.getUrl());
            }
        });

        // Save button
        holder.btnSaveUrl.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSave(stream);
            }
        });

        // Play button - open in external player
        holder.btnPlayUrl.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlay(stream);
            } else {
                openInPlayer(stream.getUrl());
            }
        });
    }

    @Override
    public int getItemCount() {
        return streams.size();
    }

    private String getTypeLabel(String type) {
        switch (type) {
            case "master":
                return "Master";
            case "audio":
                return "Audio";
            case "video":
                return "Video";
            case "playlist":
                return "Playlist";
            case "live":
                return "Live";
            case "stream":
                return "Stream";
            default:
                return "Unknown";
        }
    }

    public void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("HLS URL", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show();
    }

    public void openInPlayer(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url), "application/x-mpegURL");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            // Try generic player
            intent.setDataAndType(Uri.parse(url), "video/*");
            try {
                context.startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(context, "Немає плеєра для відтворення", Toast.LENGTH_LONG).show();
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUrl;
        TextView tvStreamType;
        TextView tvResolution;
        ImageButton btnCopyUrl;
        ImageButton btnSaveUrl;
        ImageButton btnPlayUrl;
        View metaContainer;

        public ViewHolder(View itemView) {
            super(itemView);
            tvUrl = itemView.findViewById(R.id.tvUrl);
            tvStreamType = itemView.findViewById(R.id.tvStreamType);
            tvResolution = itemView.findViewById(R.id.tvResolution);
            btnCopyUrl = itemView.findViewById(R.id.btnCopyUrl);
            btnSaveUrl = itemView.findViewById(R.id.btnSaveUrl);
            btnPlayUrl = itemView.findViewById(R.id.btnPlayUrl);
            metaContainer = itemView.findViewById(R.id.metaContainer);
        }
    }
}
