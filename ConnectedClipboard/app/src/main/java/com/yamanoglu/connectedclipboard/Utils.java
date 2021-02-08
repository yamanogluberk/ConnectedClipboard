package com.yamanoglu.connectedclipboard;

import com.yamanoglu.connectedclipboard.network.DisconnectListener;
import com.yamanoglu.connectedclipboard.view.MemberListAdapter;
import com.yamanoglu.connectedclipboard.view.MemberListChangedListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Utils {
    public static int UDPPort = 5556;
    public static int TCPPort = 5555;
    public static int PingTryCount = 3;
    public static String ip = "";
    public static String currentRoomName = "";
    public static DisconnectListener sDisconnectListener;
    public static MemberListChangedListener sMemberListChangedListener;

    public static String GetJSON(String typename, String data){
        JSONObject packet = new JSONObject();
        try {
            packet.put("IP", ip);
            packet.put("TYPE", typename);
            packet.put("DATA", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return packet.toString();
    }

    public static String GetJSON(String typename, long data){
        JSONObject packet = new JSONObject();
        try {
            packet.put("IP", ip);
            packet.put("TYPE", typename);
            packet.put("DATA", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return packet.toString();
    }

    public static String GetJSON(String typename, List<String> data){
        JSONObject packet = new JSONObject();
        try {
            JSONArray array = new JSONArray();
            for (String item:data) {
                array.put(item);
            }
            packet.put("IP", ip);
            packet.put("TYPE", typename);
            packet.put("DATA", array);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return packet.toString();
    }

    public static String GetJSON(String typename, String data, long timestamp){
        JSONObject packet = new JSONObject();
        try {
            packet.put("IP", ip);
            packet.put("TYPE", typename);
            packet.put("DATA", data);
            packet.put("TIMESTAMP", timestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return packet.toString();
    }

    public static String GetIPFromJSON(String json){
        try {
            JSONObject jsonObject = new JSONObject(json);
            return jsonObject.getString("IP");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String GetTypenameFromJSON(String  json){
        try {
            JSONObject jsonObject = new JSONObject(json);
            return jsonObject.getString("TYPE");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static long GetTimestampFromJSON(String json){
        try {
            JSONObject jsonObject = new JSONObject(json);
            return jsonObject.getLong("TIMESTAMP");
        } catch (JSONException e){
            e.printStackTrace();
        }
        return 0;
    }

    public static String GetDataAsStringFromJSON(String  json)
    {
        try {
            JSONObject jsonObject = new JSONObject(json);

            Object value = jsonObject.get("DATA");
            return String.valueOf(value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static long GetDataAsLongFromJSON(String  json)
    {
        try {
            JSONObject jsonObject = new JSONObject(json);

            return jsonObject.getLong("DATA");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static List<String> GetDataAsListFromJSON(String receivedJson) {
        try {
            JSONObject jsonObject = new JSONObject(receivedJson);
            JSONArray jsonArray = jsonObject.getJSONArray("DATA");
            List<String> result = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                result.add(jsonArray.getString(i));
            }
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}
