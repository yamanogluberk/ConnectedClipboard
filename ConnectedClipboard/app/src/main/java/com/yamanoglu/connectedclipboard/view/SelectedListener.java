package com.yamanoglu.connectedclipboard.view;

import com.yamanoglu.connectedclipboard.RoomModal;

public interface SelectedListener {
    void RoomSelected(RoomModal roomModal);
    void OnRoomSelectionDismissed();
}
