import select
import socket
import json
import threading
import time
import clipboard
import math
from datetime import datetime

ip = ""
localpart = ""
name = ""
tcp = 5555
udp = 5556
buffer_size = 1024
broadcast_try_count = 3
ping_try_count = 3
members = []  # item - (str) ipaddress
current_room_ip = ""
my_room_name = ""  # only room owner has this data
discovered_rooms = set()  # item - (roomname, roomip)
REQUESTED_ROOM = ("", "")
CLIPBOARD_DATA = clipboard.paste()
CLIPBOARD_LOCK = threading.Lock()
DATA_LOCK = threading.Lock()
SHARED_TIME_BASE = 0
PRIVATE_TIME_BASE = 0
LATENCY = 0
RECEIVED_PING_COUNTER = 0
LAST_CHANGED_TS = 0
is_main_ui = True
input_active = True


def main():
    print()
    print("*****************************************")
    print("****      WELCOME TO Clipboarder      ****")
    print("*****************************************")
    print()

    get_ip()

    listen_udp = threading.Thread(target=start_listening_udp)
    listen_udp.setDaemon(True)
    listen_udp.start()

    listen_tcp = threading.Thread(target=start_listening_tcp)
    listen_tcp.setDaemon(True)
    listen_tcp.start()

    listen_cb = threading.Thread(target=listening_clipboard)
    listen_cb.setDaemon(True)
    listen_cb.start()

    send_discover()

    main_ui_info()
    input_ui()

    listen_cb.join()
    listen_udp.join()
    listen_tcp.join()


def input_ui():
    global is_main_ui
    global input_active
    while True:
        cmd = input()
        if not input_active:
            continue

        if is_main_ui:
            splitted = cmd.strip().split(" ")
            if len(splitted) >= 2 and splitted[0] == "/create":
                create_new_room(' '.join(splitted[1:]))
            elif len(splitted) >= 2 and splitted[0] == "/join":
                input_active = False
                join_room(' '.join(splitted[1:]))
            elif len(splitted) == 1 and splitted[0] == "/quit":
                terminate()
            elif len(splitted) == 1 and splitted[0] == "/refresh":
                discovered_rooms.clear()
                main_ui_info()
                send_discover()
        else:
            if cmd.strip() == "/leave":
                leave_room()
            elif cmd.strip() == "/list":
                list_users()


def main_ui_info():
    if len(discovered_rooms) == 0:
        print()
        print("There is no active rooms in the network!")
        print()
    else:
        for item in discovered_rooms:
            print("Active rooms:")
            print()
            print(item[0])
            print()
    print("          *********************************************         ")
    print()
    print("Type /create <roomname> to create a new room")
    print("Type /refresh to refresh active room list")
    print("Type /join <roomname> to join an existing room")
    print("Type /quit to exit the application")
    print()
    print("          *********************************************         ")


def room_ui_info():
    print()
    print(f"There are {len(members)} members in the room!")
    print()
    print("          *********************************************         ")
    print()
    print("Type /leave to leave the current room")
    print("Type /list to list users in the room")
    print()
    print("          *********************************************         ")


def create_new_room(room_name):
    global is_main_ui
    global my_room_name
    global current_room_ip

    my_room_name = room_name
    current_room_ip = ip
    members.append(ip)
    print("New room created with name ", room_name)
    room_ui_info()
    is_main_ui = False


def join_room(room_name):
    global is_main_ui
    global input_active
    global REQUESTED_ROOM
    for item in discovered_rooms:
        if room_name == item[0]:
            send_connect(item[1])
            REQUESTED_ROOM = item
            return
    print()
    print("This room doesnt exist!")
    print()
    input_active = True


def leave_room():
    global current_room_ip
    global members
    global is_main_ui
    global my_room_name
    global SHARED_TIME_BASE
    global PRIVATE_TIME_BASE
    global LATENCY
    global RECEIVED_PING_COUNTER

    if current_room_ip == ip:  # DISBAND GROUP
        for mem in members:
            if mem != ip:
                send_kick(mem)
        current_room_ip = ""
        my_room_name = ""
        members.clear()
        main_ui_info()
        is_main_ui = True
    else:  # LEAVE GROUP
        send_disconnect(current_room_ip)
        current_room_ip = ""
        members.clear()
        main_ui_info()
        is_main_ui = True

    SHARED_TIME_BASE = 0
    PRIVATE_TIME_BASE = 0
    LATENCY = 0
    RECEIVED_PING_COUNTER = 0


def list_users():
    k = 1
    print("Current users:")
    for mem in members:
        print(str(k) + " -> " + mem)
        k = k + 1


def terminate():
    exit()


def get_ip():
    global ip
    global localpart
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    temp = "127.0.0.1"
    try:
        s.connect(("8.8.8.8", 80))
        temp = s.getsockname()[0]
    finally:
        s.close()
    parts = temp.split(".")
    localpart = parts[3]
    ip = temp


def start_listening_udp():
    while True:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.bind(("", udp))
            s.setblocking(False)
            result = select.select([s], [], [])
            msg = result[0][0].recv(buffer_size)
            infer_data(msg.decode())


def start_listening_tcp():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

        s.bind((ip, tcp))
        s.listen()

        while True:
            conn, addr = s.accept()
            with conn:
                data = ""
                while True:
                    temp = conn.recv(buffer_size)
                    if not temp:
                        break
                    data += temp.decode()
                handle_tcp_req = threading.Thread(target=infer_data, args=(data,))
                handle_tcp_req.setDaemon(True)
                handle_tcp_req.start()
                #infer_data(data)


def infer_data(data):
    try:
        data = json.loads(data)
        if data["IP"] == ip:
            return
        if data["TYPE"] == "DISCOVER_ROOMS":
            discover_received(data)
        elif data["TYPE"] == "RESPOND_ROOM":
            respond_received(data)
        elif data["TYPE"] == "CONNECT":
            connect_received(data)
        elif data["TYPE"] == "DISCONNECT":
            disconnect_received(data)
        elif data["TYPE"] == "CONNECTION_APPROVED":
            connection_approved_received(data)
        elif data["TYPE"] == "NEW_MEMBER":
            new_member_received(data)
        elif data["TYPE"] == "MEMBER_DISCONNECTED":
            member_disconnected_received(data)
        elif data["TYPE"] == "KICK":
            kick_received(data)
        elif data["TYPE"] == "CLIPBOARD":
            clipboard_received(data)
        elif data["TYPE"] == "PING":
            ping_received(data)
        elif data["TYPE"] == "PING_RESPOND":
            ping_respond_received(data)
        elif data["TYPE"] == "REQUEST_TIMESTAMP":
            receive_timestamp_request(data)
        elif data["TYPE"] == "RECEIVE TIMESTAMP":
            receive_timestamp(data)
    except:
        print("The received packet is not Json or not the proper practice of the protocol!")


def discover_received(data):
    if my_room_name.strip() != "":
        send_respond(data["IP"], my_room_name)


def respond_received(data):
    newroom = (data["DATA"], data["IP"])
    if newroom not in discovered_rooms:
        discovered_rooms.add(newroom)
        main_ui_info()


def connect_received(data):
    if my_room_name.strip() == "":
        print("Received connect when there is no owned room!!!")
        return
    elif data["IP"] in members:
        pass
    else:
        for mem in members:
            if mem != ip:
                send_new_member(mem, data["IP"])
        members.append(data["IP"])
        send_connection_approved(data["IP"])


def disconnect_received(data):
    if data["IP"] in members:
        members.remove(data["IP"])
        for mem in members:
            if mem != ip:
                send_member_disconnected(mem, data["IP"])


def connection_approved_received(data):
    global current_room_ip
    global members
    global is_main_ui
    global input_active
    global REQUESTED_ROOM
    global LATENCY
    global RECEIVED_PING_COUNTER

    if current_room_ip == "" and REQUESTED_ROOM[1] == data["IP"]:
        REQUESTED_ROOM = ("", "")
        current_room_ip = data["IP"]
        members = data["DATA"]
        is_main_ui = False
        input_active = True
        room_ui_info()
        for x in range(ping_try_count):
            send_ping(current_room_ip)
            with DATA_LOCK:
                LATENCY = LATENCY - get_current_timestamp()
        counter = 0
        while RECEIVED_PING_COUNTER != ping_try_count:
            time.sleep(0.1)
            counter = counter + 1
            if counter > 100:
                return
        send_timestamp_request(current_room_ip)


def send_ping(target_ip):
    data = f"{get_json('PING')}"
    send_message_tcp(data, target_ip)


def send_ping_respond(target_ip):
    data = f"{get_json('PING_RESPOND')}"
    send_message_tcp(data, target_ip)


def ping_received(data):
    global current_room_ip

    if current_room_ip == ip and data["IP"] in members:
        send_ping_respond(data["IP"])


def ping_respond_received(data):
    global current_room_ip
    global LATENCY
    global RECEIVED_PING_COUNTER

    if current_room_ip == data["IP"]:
        with DATA_LOCK:
            LATENCY = LATENCY + get_current_timestamp()
            #print("PING RESPOND RECEIVED::PING LATENCY --> " + str(LATENCY))
            RECEIVED_PING_COUNTER = RECEIVED_PING_COUNTER + 1


def send_timestamp_request(target_ip):
    data = f"{get_json('REQUEST_TIMESTAMP')}"
    send_message_tcp(data, target_ip)


def receive_timestamp_request(data):
    global current_room_ip

    if current_room_ip == ip and data["IP"] in members:
        send_timestamp(data["IP"])


def send_timestamp(target_ip):
    ct = get_current_timestamp()
    data = f"{get_json('RECEIVE TIMESTAMP', ct)}"
    send_message_tcp(data, target_ip)


def receive_timestamp(data):
    global SHARED_TIME_BASE
    global PRIVATE_TIME_BASE

    if current_room_ip == data["IP"]:
        SHARED_TIME_BASE = data["DATA"]
        SHARED_TIME_BASE = SHARED_TIME_BASE + (LATENCY / (ping_try_count * 2))
        PRIVATE_TIME_BASE = get_current_timestamp()
        print("LATENCY --> " + str((LATENCY / (ping_try_count * 2))))
        print("SHARED_TIME_BASE --> " + str(SHARED_TIME_BASE))
        print("PRIVATE_TIME_BASE --> " + str(PRIVATE_TIME_BASE))


def new_member_received(data):
    if (data["IP"] == current_room_ip) and (data["DATA"] not in members):
        members.append(data["DATA"])


def member_disconnected_received(data):
    if (data["IP"] == current_room_ip) and (data["DATA"] in members):
        members.remove(data["DATA"])


def kick_received(data):
    global current_room_ip
    global members
    global is_main_ui
    global my_room_name
    global RECEIVED_PING_COUNTER
    global SHARED_TIME_BASE
    global PRIVATE_TIME_BASE
    global LATENCY

    if data["IP"] == current_room_ip:
        current_room_ip = ""
        members.clear()
        main_ui_info()
        is_main_ui = True
        SHARED_TIME_BASE = 0
        PRIVATE_TIME_BASE = 0
        LATENCY = 0
        RECEIVED_PING_COUNTER = 0


def listening_clipboard():
    global CLIPBOARD_DATA
    global LAST_CHANGED_TS

    while True:
        with CLIPBOARD_LOCK:
            current_clipboard = clipboard.paste()
            if CLIPBOARD_DATA != current_clipboard:
                clipboard_ts = SHARED_TIME_BASE + (get_current_timestamp() - PRIVATE_TIME_BASE)
                for mem in members:
                    if mem != ip:
                        send_clipboard(mem, clipboard_ts, current_clipboard)
                CLIPBOARD_DATA = current_clipboard
                LAST_CHANGED_TS = clipboard_ts
            time.sleep(0.1)


def clipboard_received(data):
    global CLIPBOARD_DATA
    global LAST_CHANGED_TS

    with CLIPBOARD_LOCK:
        if LAST_CHANGED_TS < data["TIMESTAMP"]:
            CLIPBOARD_DATA = data["DATA"]
            LAST_CHANGED_TS = data["TIMESTAMP"]
            clipboard.copy(CLIPBOARD_DATA)


def send_clipboard(target_ip, clipboard_ts, clipboard_data):
    data = f"{get_json_ts('CLIPBOARD', clipboard_ts, clipboard_data)}"
    send_message_tcp(data, target_ip)


def send_discover():
    data = f"{get_json('DISCOVER_ROOMS')}"
    send_broadcast(data)


def send_respond(target_ip, room_name):
    data = f"{get_json('RESPOND_ROOM', room_name)}"
    send_message_tcp(data, target_ip)


def send_connect(target_ip):
    data = f"{get_json('CONNECT')}"
    send_message_tcp(data, target_ip)


def send_disconnect(target_ip):
    data = f"{get_json('DISCONNECT')}"
    send_message_tcp(data, target_ip)


def send_kick(target_ip):
    data = f"{get_json('KICK')}"
    send_message_tcp(data, target_ip)


def send_connection_approved(target_ip):
    data = f"{get_json('CONNECTION_APPROVED', members)}"
    send_message_tcp(data, target_ip)


def send_new_member(target_ip, member_ip):
    data = f"{get_json('NEW_MEMBER', member_ip)}"
    send_message_tcp(data, target_ip)


def send_member_disconnected(target_ip, member_ip):
    data = f"{get_json('MEMBER_DISCONNECTED', member_ip)}"
    send_message_tcp(data, target_ip)


def send_broadcast(data):
    for x in range(broadcast_try_count):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.bind(('', 0))
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        s.sendto(data.encode(), ('<broadcast>', udp))
        s.close()


def send_message_tcp(data, destination):
    thread = threading.Thread(target=send_message_thread, args=(data, destination), daemon=True)
    thread.start()


def send_message_thread(packet, destination):
    global current_room_ip
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(1)
            s.connect((destination, tcp))
            s.sendall(packet.encode())
    except:
        print("!! Unexpected offline member detected !!")


def get_json(typename, data=None):
    packet = {"IP": ip, "TYPE": typename, "DATA": data}
    return json.dumps(packet)


def get_json_ts(typename, timestamp, data):
    packet = {"IP": ip, "TYPE": typename, "TIMESTAMP": timestamp, "DATA": data}
    return json.dumps(packet)

  
def get_current_timestamp():
    ts = datetime.now().timestamp() * 1000
    ts = math.floor(ts)
    return ts


if __name__ == '__main__':
    main()
