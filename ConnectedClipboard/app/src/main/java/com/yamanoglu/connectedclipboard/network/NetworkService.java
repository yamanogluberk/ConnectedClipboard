package com.yamanoglu.connectedclipboard.network;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.yamanoglu.connectedclipboard.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class NetworkService extends Service implements INetworkService {

    private final IBinder binder = new NetworkBinder();

    private final List<Listener> discoverListeners = new ArrayList<>();
    private final List<Listener> respondListeners = new ArrayList<>();
    private final List<Listener> connectListeners = new ArrayList<>();
    private final List<Listener> disconnectListeners = new ArrayList<>();
    private final List<Listener> connectionApprovedListeners = new ArrayList<>();
    private final List<Listener> newMemberListeners = new ArrayList<>();
    private final List<Listener> memberDisconnectedListeners = new ArrayList<>();
    private final List<Listener> kickListeners = new ArrayList<>();
    private final List<Listener> clipboardListeners = new ArrayList<>();
    private final List<Listener> pingListeners = new ArrayList<>();
    private final List<Listener> pingRespondListeners = new ArrayList<>();
    private final List<Listener> timestampRequestListeners = new ArrayList<>();
    private final List<Listener> timestampListeners = new ArrayList<>();

    private boolean shouldRestartUDPSocketListen;
    private DatagramSocket socketUDP;

    private ServerSocket mTcpSocket;
    private boolean mTCPThreadRunning;
    private Socket mCurrentConnection;

    @Override
    public void onCreate() {
        Toast.makeText(this, "NetworkServiceStarted", Toast.LENGTH_SHORT).show();
        new Thread(this::TCPListen).start();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "NetworkServiceStopped!", Toast.LENGTH_SHORT).show();

        mTCPThreadRunning = false;
        if (mCurrentConnection != null) {
            try {
                mCurrentConnection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mTcpSocket != null) {
            try {
                mTcpSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        StopListeningUDP();
    }

    @Override
    public void StartListeningUDP() {
        shouldRestartUDPSocketListen = true;
        UDPListen();
    }

    @Override
    public void StopListeningUDP() {
        shouldRestartUDPSocketListen = false;
        if (socketUDP != null)
            socketUDP.close();
    }

    public void SendTCP(String data, String destination) {
        new Thread(() -> {
            try {
                // Connect to the server
                Socket socket = new Socket(InetAddress.getByName(destination), Utils.TCPPort);

                // Create input and output streams to read from and write to the server
                PrintStream out = new PrintStream(socket.getOutputStream());

                out.print(data);

                out.close();
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void SendBroadcast(int count) {
        new Thread(() -> {
            for (int i = 0; i < count; i++) {
                DatagramSocket socket = null;

                try {
                    InetAddress address = InetAddress.getByName("255.255.255.255");
                    socket = new DatagramSocket();
                    socket.setBroadcast(true);


                    String broadcastMessage = Utils.GetJSON("DISCOVER_ROOMS", "");
                    byte[] buffer = broadcastMessage.getBytes();

                    DatagramPacket packet
                            = new DatagramPacket(buffer, buffer.length, address, Utils.UDPPort);
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (socket != null)
                    socket.close();
            }
        }).start();
    }

    private void UDPListen() {
        new Thread(() -> {
            try {
                int port = Utils.UDPPort;
                while (shouldRestartUDPSocketListen) {
                    byte[] recvBuf = new byte[15000];
                    if (socketUDP == null || socketUDP.isClosed()) {
                        socketUDP = new DatagramSocket(port);
                        socketUDP.setBroadcast(true);
                    }
                    DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                    Log.e("NETWORKRELATED", "Waiting for UDP broadcast");
                    socketUDP.receive(packet);

                    String senderIP = packet.getAddress().getHostAddress();
                    String data = new String(packet.getData()).trim();

                    Log.e("NETWORKRELATED", "Got UDB broadcast from " + senderIP + ", data: " + data);
                    new Thread(() -> {
                        InferData(data);
                    }).start();

                    socketUDP.close();
                }
            } catch (Exception e) {
                Log.i("NETWORKRELATED", "no longer listening for UDP broadcasts cause of error " + e.getMessage());
            }
        }).start();
        Log.i("NETWORKRELATED", "UDP Listening started!");
    }

    private void TCPListen() {
        mTcpSocket = null;
        mTCPThreadRunning = true;
        try {
            mTcpSocket = new ServerSocket();
            mTcpSocket.setReuseAddress(true);
            mTcpSocket.setSoTimeout(5000);
            mTcpSocket.bind(new InetSocketAddress(Utils.TCPPort));
            while (mTCPThreadRunning) {

                // Wait for the next connection
                try {
                    mCurrentConnection = mTcpSocket.accept();
                    if (!mTCPThreadRunning) {
                        mCurrentConnection.close();
                        continue;
                    }
                    BufferedReader in = new BufferedReader(new InputStreamReader(mCurrentConnection.getInputStream()));
                    String data = "";
                    String line = in.readLine();
                    while (line != null && line.length() > 0) {
                        data += line;
                        line = in.readLine();
                    }
                    //all data received
                    in.close();

                    mCurrentConnection.close();
                    String finalData = data;
                    new Thread(() -> InferData(finalData)).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void InferData(String data) {
        if (Utils.ip.equals(Utils.GetIPFromJSON(data)))
            return;
        Log.i("NETWORKRELATED", "InferData: " + data);
        String type = Utils.GetTypenameFromJSON(data);
        switch (type) {
            case "DISCOVER_ROOMS":
                DiscoverReceived(data);
                break;
            case "RESPOND_ROOM":
                RespondReceived(data);
                break;
            case "CONNECT":
                ConnectReceived(data);
                break;
            case "DISCONNECT":
                DisconnectReceived(data);
                break;
            case "CONNECTION_APPROVED":
                ConnectionApprovedReceived(data);
                break;
            case "NEW_MEMBER":
                NewMemberReceived(data);
                break;
            case "MEMBER_DISCONNECTED":
                MemberDisconnectedReceived(data);
                break;
            case "KICK":
                KickReceived(data);
                break;
            case "CLIPBOARD":
                ClipboardReceived(data);
                break;
            case "PING":
                PingReceived(data);
                break;
            case "PING_RESPOND":
                PingRespondReceived(data);
                break;
            case "REQUEST_TIMESTAMP":
                TimestampRequestReceived(data);
                break;
            case "RECEIVE TIMESTAMP":
                TimestampReceived(data);
                break;
        }
    }

    private void DiscoverReceived(String data) {
        for (Listener listener : discoverListeners) {
            listener.DataReceived(data);
        }
    }

    private void RespondReceived(String data) {
        for (Listener listener : respondListeners) {
            listener.DataReceived(data);
        }
    }

    private void ConnectReceived(String data) {
        for (Listener listener : connectListeners) {
            listener.DataReceived(data);
        }
    }

    private void DisconnectReceived(String data) {
        for (Listener listener : disconnectListeners) {
            listener.DataReceived(data);
        }
    }

    private void ConnectionApprovedReceived(String data) {
        for (Listener listener : connectionApprovedListeners) {
            listener.DataReceived(data);
        }
    }

    private void NewMemberReceived(String data) {
        for (Listener listener : newMemberListeners) {
            listener.DataReceived(data);
        }
    }

    private void MemberDisconnectedReceived(String data) {
        for (Listener listener : memberDisconnectedListeners) {
            listener.DataReceived(data);
        }
    }

    private void KickReceived(String data) {
        for (Listener listener : kickListeners) {
            listener.DataReceived(data);
        }
    }

    private void ClipboardReceived(String data) {
        for (Listener listener : clipboardListeners) {
            listener.DataReceived(data);
        }
    }

    private void PingReceived(String data) {
        for (Listener listener : pingListeners) {
            listener.DataReceived(data);
        }
    }

    private void PingRespondReceived(String data) {
        for (Listener listener : pingRespondListeners) {
            listener.DataReceived(data);
        }
    }

    private void TimestampRequestReceived(String data) {
        for (Listener listener : timestampRequestListeners) {
            listener.DataReceived(data);
        }
    }

    private void TimestampReceived(String data) {
        for (Listener listener : timestampListeners) {
            listener.DataReceived(data);
        }
    }

    @Override
    public Unbinder RegisterToDiscover(Listener listener) {
        discoverListeners.add(listener);
        return () -> discoverListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToRespond(Listener listener) {
        respondListeners.add(listener);
        return () -> respondListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToConnect(Listener listener) {
        connectListeners.add(listener);
        return () -> connectListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToDisconnect(Listener listener) {
        disconnectListeners.add(listener);
        return () -> disconnectListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToConnectionApproved(Listener listener) {
        connectionApprovedListeners.add(listener);
        return () -> connectionApprovedListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToNewMember(Listener listener) {
        newMemberListeners.add(listener);
        return () -> newMemberListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToMemberDisconnected(Listener listener) {
        memberDisconnectedListeners.add(listener);
        return () -> memberDisconnectedListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToKick(Listener listener) {
        kickListeners.add(listener);
        return () -> kickListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToClipboard(Listener listener) {
        clipboardListeners.add(listener);
        return () -> clipboardListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToPing(Listener listener) {
        pingListeners.add(listener);
        return () -> pingListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToPingRespond(Listener listener) {
        pingRespondListeners.add(listener);
        return () -> pingRespondListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToTimestampRequest(Listener listener) {
        timestampRequestListeners.add(listener);
        return () -> timestampRequestListeners.remove(listener);
    }

    @Override
    public Unbinder RegisterToTimeStamp(Listener listener) {
        timestampListeners.add(listener);
        return () -> timestampListeners.remove(listener);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class NetworkBinder extends Binder {
        public INetworkService GetNetwork() {
            return NetworkService.this;
        }
    }
}
