import select
import socket
import json
import threading

ip = ""
localpart = ""
name = ""
tcp = 5555
udp = 5556
buffer_size = 1024
broadcast_try_count = 3
members = []  # item - (str) ipaddress
current_room_ip = ""
my_room_name = ""  # only room owner has this data
discovered_rooms = set()  # item - (roomname, roomip)

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

    send_discover()

    main_ui_info()
    input_ui()

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


def main_ui_info():
    if len(discovered_rooms) == 0:
        print()
        print("There is no active rooms in the network!")
        print()
    else:
        for item in discovered_rooms:
            print("Active rooms:")
            print()
            print(item[1])
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
    for item in discovered_rooms:
        if room_name == item[0]:
            send_connect(item[1])
            return
    print()
    print("This room doesnt exist!")
    print()
    input_active = True


def leave_room():
    global current_room_ip
    global members
    global is_main_ui

    send_disconnect(current_room_ip)
    current_room_ip = ""
    members.clear()
    main_ui_info()
    is_main_ui = True


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
                infer_data(data)


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
        elif data["TYPE"] == "CLIPBOARD":
            clipboard_received(data)
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
    else:
        send_connection_approved(data["IP"])
        # TODO send new user to current members


def disconnect_received(data):
    print("disconnect")


def connection_approved_received(data):
    global current_room_ip
    global members
    global is_main_ui
    global input_active

    current_room_ip = data["IP"]
    members = data["DATA"]
    is_main_ui = False
    input_active = True
    room_ui_info()


def new_member_received(data):
    2 + 3


def member_disconnected_received(data):
    2 + 3


def clipboard_received(data):
    2 + 3


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


def send_connection_approved(target_ip):
    data = f"{get_json('CONNECTION_APPROVED', members)}"
    send_message_tcp(data, target_ip)


def send_new_member(target_ip, member_ip):
    data = f"{get_json('NEW_MEMBER', member_ip)}"
    send_message_tcp(data, target_ip)


def send_member_disconnected(target_ip, member_ip):
    data = f"{get_json('MEMBER_DISCONNECTED', member_ip)}"
    send_message_tcp(data, target_ip)


def send_clipboard(target_ip, clipboard_content):
    data = f"{get_json('CLIPBOARD', clipboard_content)}"
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


if __name__ == '__main__':
    main()
