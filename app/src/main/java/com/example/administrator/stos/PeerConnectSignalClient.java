package com.example.administrator.stos;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

interface PeerConnectSignalClientObserver
{
    public abstract void OnSignedIn();
    public abstract void OnDisconnected();
    public abstract void OnPeerConnected(List<Integer> listFriends);
    public abstract void OnPeerDisconnected();
    public abstract void OnMessageFromPeer(int peerId, String message);
    public abstract void OnMessageSent();
    public abstract void OnServerConnectionFailure();

    public abstract void OnPeerOffer(SessionDescription offer);
    public abstract void OnPeerAnswer(SessionDescription answer);
    public abstract void OnPeerCandidate(IceCandidate candidate);

}

class SignalConstParam
{
    static final String kByeMessage = "BYE";
    static final String kSrcId = "srcId";
    static final String kDstId = "dstId";

    static final String kFriendList = "friendList";
    static final String kId = "id";
}

public  class PeerConnectSignalClient implements RTCWebSocketHandler
{
     PeerConnectSignalClient(PeerConnectSignalClientObserver  observer)
    {
        pcObserver = observer;
        myId = -1;
        peerId = -1;
    }

    private PeerConnectSignalClientObserver  pcObserver;
    private RTCWebSocketClient rtcWebSocketClient;
    private int myId ;
    private int peerId;

    public void Init()
    {
        rtcWebSocketClient.RegisterWebSoocketHandler(this);
    }

    public int Connect(String ip, int port, String clientName)
    {
        try
        {
            if (rtcWebSocketClient == null)
            {
                String url = String.format("ws://%s:%d", ip, port );
                rtcWebSocketClient = new RTCWebSocketClient(new URI(url));  //"ws://localhost:8887"
                Init();
            }
            rtcWebSocketClient.connect();
        }
        catch (URISyntaxException e)
        {
            System.out.println("connetc to singal server failed.");
        }

        return 0;
    }

    private int ParsePeerId(String message)
    {
        return  0;
    }

    private int ParseFriends(String data, List<Integer> listData)
    {
        int nRet = 0;
        try
        {
            JSONObject obj = new JSONObject(data);
            JSONArray ary = obj.optJSONArray(SignalConstParam.kFriendList);

            if (ary != null)
            {
                JSONObject tmp;
                for (int i = 0; i < ary.length(); ++i)
                {
                    tmp = ary.getJSONObject(i);
                    listData.add(tmp.optInt(SignalConstParam.kId));
                }
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }

        return nRet;
    }

    @Override
    public void OnOpen(ServerHandshake var1)
    {
       short httpCode = var1.getHttpStatus();
       String statusMsg = var1.getHttpStatusMessage();
        if (101 != httpCode)
        {
            Log.e("PeerConnectSignalClient::OnOpen", "connect failed.");
            return;
        }

  /*      String data = new String(var1.getContent());
        if (!data.isEmpty())
        {
            List<Integer> listData = new LinkedList<Integer>();
            ParseFriends(data, listData);
            pcObserver.OnPeerConnected(listData);
        }*/

    }

    @Override
    public void OnClose()
    {
        Log.e("PeerConnectSignalClient::OnClose", "offline.");
    }

    @Override
    public void OnMessage(String message)
    {
        int id =  ParsePeerId(message);
        if (myId == -1 && id != -1)
        {
            myId = id;
        }

        if (myId != id)
        {
            Log.e("PeerConnectSignalClient::OnMessage", "peerid error.");
            return;
        }

        if (message.length() == SignalConstParam.kByeMessage.length() && message == SignalConstParam.kByeMessage)
        {
            pcObserver.OnDisconnected();
        }
        else
        {
            try
            {
                JSONObject jsobj = new JSONObject(message);
                String type = jsobj.optString(ConstParams.kSessionDescriptionTypeName);

                if (type.equals("login"))
                {
                    String data = new String(message);
                    if (!data.isEmpty())
                    {
                        myId = jsobj.optInt("myid");
                        JSONArray friendsArray = jsobj.optJSONArray("friendslist");
                        List<Integer> listData = new LinkedList<Integer>();

                        for (int i = 0; i < friendsArray.length(); i++)
                        {
                            int idVal = (Integer)friendsArray.optInt(i);
                            listData.add(idVal);
                        }
                     /*   listData.add(10);
                        listData.add(20);
                        listData.add(30);
                        listData.add(60);*/
                        pcObserver.OnPeerConnected(listData);
                    }

                }
                else if (type.equals("newlogin"))
                {
                    String data = new String(message);
                    if (!data.isEmpty())
                    {
                        int newLoginId = jsobj.optInt("id");
                        List<Integer> listData = new LinkedList<Integer>();
                        listData.add(newLoginId);

                        pcObserver.OnPeerConnected(listData);
                    }
                }
                else if (type.equals("offline"))
                {

                }
                else if (type.equals("offer"))
                {
                    int srcId = jsobj.optInt(SignalConstParam.kSrcId);
                    if (srcId != -1)
                    {
                        peerId = srcId;
                    }

                    String sdp = jsobj.optString(ConstParams.kSessionDescriptionSdpName);
                    SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                    pcObserver.OnPeerOffer(offer);
                }
                else if (type.equals("answer"))
                {
                    int srcId = jsobj.optInt(SignalConstParam.kSrcId);
                    int dstId = jsobj.optInt(SignalConstParam.kDstId);
                    if (srcId != peerId || dstId != myId)
                    {
                       return;
                    }

                    String sdp = jsobj.optString(ConstParams.kSessionDescriptionSdpName);
                    SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                    pcObserver.OnPeerAnswer(answer);
                }
                else if (type.equals("candidate"))
                {
                    String sdpMid = jsobj.optString(ConstParams.kCandidateSdpMidName);
                    int sdpMLineIndex = jsobj.optInt(ConstParams.kCandidateSdpMlineIndexName);
                    String sdp = jsobj.optString(ConstParams.kCandidateSdpName);
                    IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                    pcObserver.OnPeerCandidate(candidate);
                }
            }
            catch (JSONException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void OnError()
    {
        System.err.println("an error occurred:");
    }

    public void SendOfferSdp(final SessionDescription offer, int peerId)
    {
        if (peerId < 0)
        {
            return;
        }

        this.peerId = peerId;
        JSONObject obj = new JSONObject();

        try
        {
            obj.putOpt(SignalConstParam.kSrcId, myId);
            obj.putOpt(SignalConstParam.kDstId, peerId);
            obj.putOpt(ConstParams.kSessionDescriptionTypeName, "offer");
            obj.putOpt(ConstParams.kSessionDescriptionSdpName, offer.description);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
        rtcWebSocketClient.send(obj.toString());
    }

    public void SendAnswerSdp(final SessionDescription answer)
    {
        JSONObject obj = new JSONObject();

        try
        {
            obj.putOpt(SignalConstParam.kSrcId, myId);
            obj.putOpt(SignalConstParam.kDstId, this.peerId);
            obj.putOpt(ConstParams.kSessionDescriptionTypeName, "answer");
            obj.putOpt(ConstParams.kSessionDescriptionSdpName, answer.description);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
        rtcWebSocketClient.send(obj.toString());
    }

    public void SendCandidate(final IceCandidate candidate)
    {
        JSONObject obj = new JSONObject();

        try
        {
            obj.putOpt(ConstParams.kSessionDescriptionTypeName, "candidate");
            obj.putOpt(ConstParams.kCandidateSdpMidName, candidate.sdpMid);
            obj.putOpt(ConstParams.kCandidateSdpMlineIndexName, candidate.sdpMLineIndex);
            obj.putOpt(ConstParams.kCandidateSdpName, candidate.sdp);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
        rtcWebSocketClient.send(obj.toString());
    }
}