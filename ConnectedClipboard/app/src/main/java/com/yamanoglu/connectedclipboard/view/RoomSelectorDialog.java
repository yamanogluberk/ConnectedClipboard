package com.yamanoglu.connectedclipboard.view;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yamanoglu.connectedclipboard.R;
import com.yamanoglu.connectedclipboard.RoomModal;
import com.yamanoglu.connectedclipboard.view.RoomListAdapter;
import com.yamanoglu.connectedclipboard.view.SelectedListener;

import java.util.ArrayList;
import java.util.List;

public class RoomSelectorDialog extends DialogFragment {


    private final SelectedListener mSelectedListener;
    private RecyclerView mRecyclerView;
    private RoomListAdapter mAdapter;
    private List<RoomModal> mDataList;

    public RoomSelectorDialog(SelectedListener listener){
        mSelectedListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        View view = inflater.inflate(R.layout.dialog_room_list, null);
        mRecyclerView = view.findViewById(R.id.rv_room_list);
        mAdapter = new RoomListAdapter();
        mAdapter.SetListener( mSelectedListener);
        mDataList = new ArrayList<RoomModal>();
//        for (int i = 0; i < 5; i++) {
//            RoomModal sampleItem = new RoomModal();
//            sampleItem.setIp("192.168.0." + i);
//            sampleItem.setName("ODa" + i);
//            mDataList.add(sampleItem);
//        }
        mAdapter.SetData(mDataList);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setAdapter(mAdapter);
        builder.setView(view);
        builder.setOnDismissListener(dialogInterface -> {
            if (mSelectedListener!=null)
                mSelectedListener.OnRoomSelectionDismissed();
        });

        return builder.create();
    }

    public void AddNewRoom(RoomModal roomModal){
        if (mDataList.contains(roomModal))
            return;
        mDataList.add(roomModal);
        mAdapter.notifyDataSetChanged();
    }
}
