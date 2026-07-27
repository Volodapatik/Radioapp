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
 * Each item shows the URL with Copy/Play/Save buttons.
 * BottomSheet will NOT close when buttons are clicked.
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

        // Set URL - full visible
        holder.tvUrl.setText(stream.getUrl());
        holder.tvUrl.setTextIsSelectable(true);

        // Set source
        String source = stream.getSource() != null ? stream.getSource() : "unknown";
        holder.tvSource.setText("Source: " + capitalize(source));

        // Copy button
        holder.btnCopyUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Copy the actual URL to clipboard
                copyToClipboard(stream.getUrl());
            }
        });

        // Save button
        holder.btnSaveUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onSave(stream);
                }
            }
        });

        // Play button - open in external player
        holder.btnPlayUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onPlay(stream);
                } else {
                    openInPlayer(stream.getUrl());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return streams.size();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("HLS URL", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, "URL скопійовано!", Toast.LENGTH_SHORT).show();
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
        TextView tvSource;
        ImageButton btnCopyUrl;
        ImageButton btnSaveUrl;
        ImageButton btnPlayUrl;

        public ViewHolder(View itemView) {
            super(itemView);
            tvUrl = itemView.findViewById(R.id.tvUrl);
            tvSource = itemView.findViewById(R.id.tvSource);
            btnCopyUrl = itemView.findViewById(R.id.btnCopyUrl);
            btnSaveUrl = itemView.findViewById(R.id.btnSaveUrl);
            btnPlayUrl = itemView.findViewById(R.id.btnPlayUrl);
        }
    }
}
