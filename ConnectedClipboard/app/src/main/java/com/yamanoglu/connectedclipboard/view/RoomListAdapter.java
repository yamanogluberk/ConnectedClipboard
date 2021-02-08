package com.yamanoglu.connectedclipboard.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yamanoglu.connectedclipboard.R;
import com.yamanoglu.connectedclipboard.RoomModal;

import java.util.List;

public class RoomListAdapter extends RecyclerView.Adapter<RoomListAdapter.ViewHolder> {

    private List<RoomModal> dataList;
    private SelectedListener mListener;

    public void SetData(List<RoomModal> data) {
        dataList = data;
        notifyDataSetChanged();
    }

    public void SetListener(SelectedListener listener){
        mListener = listener;
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

        public void Bind(RoomModal roomModal) {
            TextView textView = itemView.findViewById(R.id.tv_room_name);
            textView.setText(roomModal.getName());
            textView.setOnClickListener(view -> {
//                SocketService.ConnectRoom(roomModal.getIp());
                if (mListener!=null)
                    mListener.RoomSelected(roomModal);

            });
        }
    }
}
