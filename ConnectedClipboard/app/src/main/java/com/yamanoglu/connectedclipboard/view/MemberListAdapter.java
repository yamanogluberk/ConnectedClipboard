package com.yamanoglu.connectedclipboard.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yamanoglu.connectedclipboard.R;
import com.yamanoglu.connectedclipboard.RoomModal;
import com.yamanoglu.connectedclipboard.Utils;

import java.util.List;

public class MemberListAdapter extends RecyclerView.Adapter<MemberListAdapter.ViewHolder> {

    private List<String> dataList;

    public void SetData(List<String> data) {
        dataList = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.Bind(dataList.get(position));
    }

    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {


        public ViewHolder(@NonNull View itemView) {

            super(itemView);
        }

        public void Bind(String ip) {
            TextView textView = itemView.findViewById(R.id.tv_room_name);
            if (Utils.ip.equals(ip))
                ip = ip + "(You)";
            textView.setText(ip);
        }
    }
}
