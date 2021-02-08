package com.yamanoglu.connectedclipboard;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.yamanoglu.connectedclipboard.network.INetworkService;
import com.yamanoglu.connectedclipboard.network.NetworkService;
import com.yamanoglu.connectedclipboard.network.Unbinder;
import com.yamanoglu.connectedclipboard.view.MainActivity;

import java.util.ArrayList;
import java.util.List;


public class ClipboardListener extends Service {

    private ClipboardManager mClipboardManager;
    private final int ONGOING_NOTIFICATION_ID = 123;
    private boolean isServer;

    private String myIP;
    private INetworkService mNetworkService;
    private String roomName;
    private List<String> members = new ArrayList<>();
    private String currentRoomIp;

    private long latency;
    private int receivedPingCounter;
    private long sharedTimeBase;
    private long privateTimeBase;

    private String lastClipboardData;
    private long lastChangedTimestamp;

    private final Object dataLock = new Object();
    private final Object clipboardLock = new Object();
    private RoomModal targetRoom;

    private List<Unbinder> mUnbinders = new ArrayList<>();

    public ClipboardListener() {
    }

    @Override
    public void onCreate() {
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        mClipboardManager.addPrimaryClipChangedListener(mOnClipChangedListener);
//        Toast.makeText(this, "ServiceStarted!", Toast.LENGTH_LONG).show();
        ClipData clipData = mClipboardManager.getPrimaryClip();
        if (clipData != null) {
            lastClipboardData = clipData.getItemAt(0).coerceToText(this).toString();
            Log.d("CLIPBOARDLISTENER", lastClipboardData);
        }
        Log.d("CLIPBOARDLISTENER", "ClipboardListener oncreate");

        myIP = Utils.ip;
        Log.i("NETWORKRELATED", "ip: " + myIP);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isServer = intent.getBooleanExtra("isServer", false);
        roomName = intent.getStringExtra("roomname");
        targetRoom = (RoomModal) intent.getSerializableExtra("targetroom");
        CreateNotificationAndGoForeground();
        Log.i("NETWORKRELATED", "started for " + isServer);

        Intent bindIntent = new Intent(this, NetworkService.class);
        bindService(bindIntent, connection, BIND_AUTO_CREATE);

        return START_STICKY;
    }

    private void CreateNotificationAndGoForeground() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification.Builder notificationBuilder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationBuilder = new Notification.Builder(this, "connected_clipboard");

        } else {
            notificationBuilder = new Notification.Builder(this);
        }

        Notification notification = notificationBuilder.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContentTitle("ConnectedClipboard")
                .setContentText("Listening")
                .setContentIntent(pendingIntent)
                .build();

        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }

    private void NetworkReady() {
        if (isServer) {
            currentRoomIp = myIP;
            members.add(currentRoomIp);
            MemberListChanged();
            mNetworkService.StartListeningUDP();
            mUnbinders.add(mNetworkService.RegisterToDiscover(this::DiscoverReceived));
        }
        mUnbinders.add(mNetworkService.RegisterToConnect(this::ConnectReceived));
        mUnbinders.add(mNetworkService.RegisterToDisconnect(this::DisconnectReceived));
        mUnbinders.add(mNetworkService.RegisterToConnectionApproved(this::ConnectionApprovedReceived));
        mUnbinders.add(mNetworkService.RegisterToNewMember(this::NewMemberReceived));
        mUnbinders.add(mNetworkService.RegisterToMemberDisconnected(this::MemberDisconnectedReceived));
        mUnbinders.add(mNetworkService.RegisterToKick(this::KickReceived));
        mUnbinders.add(mNetworkService.RegisterToClipboard(this::ClipboardReceived));
        mUnbinders.add(mNetworkService.RegisterToPing(this::PingReceived));
        mUnbinders.add(mNetworkService.RegisterToPingRespond(this::PingRespondReceived));
        mUnbinders.add(mNetworkService.RegisterToTimestampRequest(this::TimestampRequestReceived));
        mUnbinders.add(mNetworkService.RegisterToTimeStamp(this::TimestampReceived));

        if (targetRoom != null && !targetRoom.getIp().trim().isEmpty())
            SendConnect(targetRoom.getIp());
    }

    private void DiscoverReceived(String receivedJson) {
        Log.i("NETWORKRELATED", "DiscoverReceived: " + System.currentTimeMillis());
        SendRespond(Utils.GetIPFromJSON(receivedJson));
    }

    private void ConnectReceived(String receivedJson) {
        if (!isServer)
            return;
        String newMemberIp = Utils.GetIPFromJSON(receivedJson);
        if (members.contains(newMemberIp))
            return;

        for (String member : members) {
            if (!member.equals(newMemberIp))
                SendNewMember(member, newMemberIp);
        }
        members.add(newMemberIp);
        MemberListChanged();
        SendConnectionApproved(newMemberIp);
    }

    private void DisconnectReceived(String receivedJson) {
        if (!isServer)
            return;
        String ip = Utils.GetIPFromJSON(receivedJson);
        if (!members.contains(ip))
            return;

        members.remove(ip);
        MemberListChanged();
        for (String member : members) {
            if (!member.equals(myIP))
                SendMemberDisconnected(member, ip);
        }
    }

    private void ConnectionApprovedReceived(String receivedJson) {
        String ip = Utils.GetIPFromJSON(receivedJson);
        if (targetRoom == null || !targetRoom.getIp().equals(ip))
            return;
        targetRoom = null;
        currentRoomIp = ip;
        members = Utils.GetDataAsListFromJSON(receivedJson);
        MemberListChanged();

        for (int i = 0; i < Utils.PingTryCount; i++) {
            SendPing(currentRoomIp);
            synchronized (dataLock) {
                latency = latency - System.currentTimeMillis();
            }
        }
        int counter = 0;
        while (receivedPingCounter != Utils.PingTryCount) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            counter++;
            if (counter > 100)
                return;
        }
        SendTimestampRequest(currentRoomIp);
    }

    private void PingReceived(String receivedJson) {
        String ip = Utils.GetIPFromJSON(receivedJson);
        if (currentRoomIp.equals(ip) && members.contains(ip))
            SendPingRespond(ip);
    }

    private void PingRespondReceived(String receivedJson) {
        String ip = Utils.GetIPFromJSON(receivedJson);
        if (currentRoomIp.equals(ip)) {
            synchronized (dataLock) {
                latency = latency + System.currentTimeMillis();
                receivedPingCounter++;
            }
        }
    }

    private void TimestampRequestReceived(String receivedJson) {
        String ip = Utils.GetIPFromJSON(receivedJson);
        if (isServer && members.contains(ip)) {
            SendTimestamp(ip);
        }
    }

    private void TimestampReceived(String receivedJson) {
        String ip = Utils.GetIPFromJSON(receivedJson);
        if (!ip.equals(currentRoomIp))
            return;

        sharedTimeBase = Utils.GetDataAsLongFromJSON(receivedJson);
        sharedTimeBase = sharedTimeBase + (latency / (Utils.PingTryCount * 2));
        privateTimeBase = System.currentTimeMillis();
        Log.i("NETWORKRELATED", "Latency: " + (latency / (Utils.PingTryCount * 2)));
        Log.i("NETWORKRELATED", "SharedTimeBase: " + sharedTimeBase);
        Log.i("NETWORKRELATED", "PrivateTimeBase: " + privateTimeBase);
    }

    private void NewMemberReceived(String receivedJson) {
        String ip = Utils.GetIPFromJSON(receivedJson);
        String newMember = Utils.GetDataAsStringFromJSON(receivedJson);
        if (ip.equals(currentRoomIp) && !members.contains(newMember)) {
            members.add(newMember);
            MemberListChanged();
        }
    }

    private void MemberDisconnectedReceived(String receivedJson) {
        String ip = Utils.GetIPFromJSON(receivedJson);
        String newMember = Utils.GetDataAsStringFromJSON(receivedJson);
        if (ip.equals(currentRoomIp)) {
            members.remove(newMember);
            MemberListChanged();
        }
    }

    private void KickReceived(String receivedJson) {
        String ip = Utils.GetIPFromJSON(receivedJson);
        if (ip.equals(currentRoomIp)) {
            currentRoomIp = "";
            members.clear();
            MemberListChanged();
            sharedTimeBase = 0;
            privateTimeBase = 0;
            latency = 0;
            receivedPingCounter = 0;
            if (Utils.sDisconnectListener != null)
                Utils.sDisconnectListener.Disconnected();
        }
    }

    private void ClipboardReceived(String receivedJson) {
        synchronized (clipboardLock) {
            if (lastChangedTimestamp < Utils.GetTimestampFromJSON(receivedJson)) {
                lastChangedTimestamp = Utils.GetTimestampFromJSON(receivedJson);
                lastClipboardData = Utils.GetDataAsStringFromJSON(receivedJson);
                ClipData clip = ClipData.newPlainText("simple text", lastClipboardData);
                mClipboardManager.setPrimaryClip(clip);
                Toast.makeText(this, "New Clipboard data received!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void SendRespond(String targetIp) {
        String packet = Utils.GetJSON("RESPOND_ROOM", roomName);
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendConnect(String targetIp) {
        String packet = Utils.GetJSON("CONNECT", "");
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendDisconnect(String targetIp) {
        String packet = Utils.GetJSON("DISCONNECT", "");
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendConnectionApproved(String targetIp) {
        String packet = Utils.GetJSON("CONNECTION_APPROVED", members);
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendNewMember(String targetIp, String memberIp) {
        String packet = Utils.GetJSON("NEW_MEMBER", memberIp);
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendMemberDisconnected(String targetIp, String memberIp) {
        String packet = Utils.GetJSON("MEMBER_DISCONNECTED", memberIp);
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendKick(String targetIp) {
        String packet = Utils.GetJSON("KICK", "");
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendPing(String targetIp) {
        String packet = Utils.GetJSON("PING", "");
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendPingRespond(String targetIp) {
        String packet = Utils.GetJSON("PING_RESPOND", "");
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendTimestampRequest(String targetIp) {
        String packet = Utils.GetJSON("REQUEST_TIMESTAMP", "");
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendTimestamp(String targetIp) {
        String packet = Utils.GetJSON("RECEIVE TIMESTAMP", System.currentTimeMillis());
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void SendClipboard(String targetIp, long clipboardTimestamp, String clipboardData) {
        String packet = Utils.GetJSON("CLIPBOARD", clipboardData, clipboardTimestamp);
        mNetworkService.SendTCP(packet, targetIp);
    }

    private void MemberListChanged() {
        if (Utils.sMemberListChangedListener != null)
            Utils.sMemberListChangedListener.MemberListChanged(members);
    }

    @Override
    public void onDestroy() {

        if (mClipboardManager != null) {
            mClipboardManager.removePrimaryClipChangedListener(mOnClipChangedListener);
            mClipboardManager = null;
            lastClipboardData = null;
        }

        mNetworkService.StopListeningUDP();

        UnregisterFromService();

        if (isServer) {
            for (String member : members) {
                if (!member.equals(myIP))
                    SendKick(member);
            }
        } else {
            SendDisconnect(currentRoomIp);
        }
        currentRoomIp = null;
        roomName = null;
        members.clear();
        MemberListChanged();
        sharedTimeBase = 0;
        privateTimeBase = 0;
        latency = 0;
        receivedPingCounter = 0;
        Log.d("CLIPBOARDLISTENER", "ClipboardListener ondestroy");
//        Toast.makeText(this, "ServiceStopped!", Toast.LENGTH_LONG).show();
        unbindService(connection);
    }

    private void UnregisterFromService() {
        for (Unbinder unbinder : mUnbinders)
            unbinder.Unbind();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            mNetworkService = ((NetworkService.NetworkBinder) service).GetNetwork();
            NetworkReady();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Toast.makeText(ClipboardListener.this,
                    "Disconnected from network service!",
                    Toast.LENGTH_SHORT).show();
        }
    };

    private final ClipboardManager.OnPrimaryClipChangedListener mOnClipChangedListener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    synchronized (clipboardLock) {
                        ClipData clipData = mClipboardManager.getPrimaryClip();
                        if (clipData != null) {
                            String current = clipData.getItemAt(0).coerceToText(ClipboardListener.this).toString();
                            if (!current.trim().equals(lastClipboardData.trim())) {
                                long timestamp = sharedTimeBase + (System.currentTimeMillis() - privateTimeBase);
                                lastClipboardData = current;
                                Log.d("CLIPBOARDLISTENER", lastClipboardData);
                                lastChangedTimestamp = timestamp;
                                for (String member : members) {
                                    if (!member.equals(myIP))
                                        SendClipboard(member, timestamp, current);
                                }
                            }
                        }
                    }
                }
            };
}