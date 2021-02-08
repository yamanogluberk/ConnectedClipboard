package com.yamanoglu.connectedclipboard.network;

public interface INetworkService {
    void StartListeningUDP();
    void StopListeningUDP();
    void SendTCP(String data, String destination);
    void SendBroadcast(int count);
    Unbinder RegisterToDiscover(Listener listener);
    Unbinder RegisterToRespond(Listener listener);
    Unbinder RegisterToConnect(Listener listener);
    Unbinder RegisterToDisconnect(Listener listener);
    Unbinder RegisterToConnectionApproved(Listener listener);
    Unbinder RegisterToNewMember(Listener listener);
    Unbinder RegisterToMemberDisconnected(Listener listener);
    Unbinder RegisterToKick(Listener listener);
    Unbinder RegisterToClipboard(Listener listener);
    Unbinder RegisterToPing(Listener listener);
    Unbinder RegisterToPingRespond(Listener listener);
    Unbinder RegisterToTimestampRequest(Listener listener);
    Unbinder RegisterToTimeStamp(Listener listener);
}

