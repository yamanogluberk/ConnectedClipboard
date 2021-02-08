package com.yamanoglu.connectedclipboard.view;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.yamanoglu.connectedclipboard.R;

public class CreateRoomDialog extends DialogFragment {

    private final RoomCreateListener mListener;

    private EditText mEditText;

    public CreateRoomDialog(RoomCreateListener listener){
        mListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        View view = inflater.inflate(R.layout.dialog_create_room, null);
        mEditText = view.findViewById(R.id.et_room_name);
        builder.setView(view);
        builder.setTitle("Create A New Room");
        builder.setNegativeButton("Cancel", (dialogInterface, i) -> {
            if (mListener!=null)
                mListener.CreateRoomCanceled();
            dismiss();
        });
        builder.setPositiveButton("Create", (dialogInterface, i) -> {
            if (mEditText.getText().toString().isEmpty()){
                Toast.makeText(getContext(), "Room name cant be empty!", Toast.LENGTH_SHORT).show();
                if (mListener!=null)
                    mListener.CreateRoomCanceled();
                return;
            }

            if (mListener!=null)
                mListener.CreateRoom(mEditText.getText().toString());
            dismiss();
        });

        return builder.create();
    }
}
