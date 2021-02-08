package com.yamanoglu.connectedclipboard.view;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.yamanoglu.connectedclipboard.ClipboardListener;
import com.yamanoglu.connectedclipboard.network.DisconnectListener;
import com.yamanoglu.connectedclipboard.R;
import com.yamanoglu.connectedclipboard.RoomModal;
import com.yamanoglu.connectedclipboard.Utils;
import com.yamanoglu.connectedclipboard.network.INetworkService;
import com.yamanoglu.connectedclipboard.network.Listener;
import com.yamanoglu.connectedclipboard.network.NetworkService;
import com.yamanoglu.connectedclipboard.network.Unbinder;

import java.util.List;


public class MainActivity extends AppCompatActivity implements SelectedListener, RoomCreateListener, DisconnectListener {


    private Button mButton;
    private Button mCreateButton;
    private RecyclerView mMemberList;
    private MemberListAdapter mMemberListAdapter;
    private boolean isInRoom;
    private DialogFragment mCreateRoomDialog;
    private RoomSelectorDialog mRoomSelectorDialog;
    private boolean mTCPListening;
    private INetworkService mNetworkService;
    private Unbinder mUnbinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mButton = findViewById(R.id.btn_join);
        mCreateButton = findViewById(R.id.btn_create);
        mMemberList = findViewById(R.id.rv_member_list);
        mMemberList.setLayoutManager(new LinearLayoutManager(this));
        mMemberListAdapter = new MemberListAdapter();
        mMemberList.setAdapter(mMemberListAdapter);
        mButton.setOnClickListener(view -> OnButtonClicked());
        Utils.sDisconnectListener = this;
        Utils.sMemberListChangedListener = this::MemberListChanged;
        if (isServiceRunning(ClipboardListener.class)) {
            JoinedRoom(Utils.currentRoomName);
        } else {
            LeaveRoom();
        }

        mCreateButton.setOnClickListener(view -> {
            mCreateRoomDialog = new CreateRoomDialog(this);
            mCreateRoomDialog.show(getSupportFragmentManager(), "createroom");
        });

        Intent intent = new Intent(this, NetworkService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    private void OnButtonClicked() {
        if (isInRoom) {
            LeaveRoom();
        } else {
            mTCPListening = true;
            if (mNetworkService != null)
                mNetworkService.SendBroadcast(3);
            mRoomSelectorDialog = new RoomSelectorDialog(this);
            mRoomSelectorDialog.show(getSupportFragmentManager(), "roomselector");
        }
    }

    @Override
    public void RoomSelected(RoomModal roomModal) {
        mRoomSelectorDialog.dismiss();
        mTCPListening = false;
        StartService(false, roomModal);
        JoinedRoom(roomModal.getName());
    }

    @Override
    public void OnRoomSelectionDismissed() {
        mTCPListening = false;
    }

    @Override
    public void CreateRoom(String roomName) {
        RoomModal roomModal = new RoomModal();
        roomModal.setName(roomName);
        roomModal.setIp("");
        StartService(true, roomModal);
        JoinedRoom(roomName);
    }

    @Override
    public void CreateRoomCanceled() {
    }

    private void LeaveRoom() {
        mCreateButton.setVisibility(View.VISIBLE);
        mMemberList.setVisibility(View.INVISIBLE);
        Utils.currentRoomName = "";
        mButton.setText("Connect");
        isInRoom = false;

        if (isServiceRunning(ClipboardListener.class))
            stopService(new Intent(MainActivity.this, ClipboardListener.class));
    }

    private void JoinedRoom(String roomName) {
        Utils.currentRoomName = roomName;
        Toast.makeText(this, "Joined Room : " + roomName, Toast.LENGTH_SHORT).show();
        mCreateButton.setVisibility(View.INVISIBLE);
        mMemberList.setVisibility(View.VISIBLE);
        mButton.setText("Disconnect");
        isInRoom = true;
    }

    private void MemberListChanged(List<String> memberList){
        runOnUiThread(() -> {
            if (mMemberListAdapter!=null)
                mMemberListAdapter.SetData(memberList);
        });
    }

    private final Listener RespondReceivedListener = new Listener() {
        @Override
        public void DataReceived(String data) {
            if (!mTCPListening)
                return;
            runOnUiThread(() -> {
                RoomModal roomModal = new RoomModal();
                roomModal.setIp(Utils.GetIPFromJSON(data));
                String name = ((String) Utils.GetDataAsStringFromJSON(data));
                roomModal.setName(name);
                mRoomSelectorDialog.AddNewRoom(roomModal);
            });
        }
    };

    private void StartService(boolean isServer, RoomModal roomModal) {
        Intent intent = new Intent(MainActivity.this, ClipboardListener.class);
        intent.putExtra("isServer", isServer);
        intent.putExtra("roomname", roomModal.getName());
        intent.putExtra("targetroom", roomModal);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Utils.sDisconnectListener = null;
        Utils.sMemberListChangedListener = null;
        if (mUnbinder!=null)
            mUnbinder.Unbind();
        mNetworkService = null;
        unbindService(connection);
    }

    @Override
    public void Disconnected() {
        runOnUiThread(this::LeaveRoom);
    }

    private final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            mNetworkService = ((NetworkService.NetworkBinder) service).GetNetwork();
            mUnbinder = mNetworkService.RegisterToRespond(RespondReceivedListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Toast.makeText(MainActivity.this,
                    "Disconnected from network service!",
                    Toast.LENGTH_SHORT).show();
        }
    };

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}