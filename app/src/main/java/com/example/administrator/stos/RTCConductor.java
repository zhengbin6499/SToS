package com.example.administrator.stos;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoRenderer;

import android.content.Context;
import java.util.List;


public class RTCConductor implements MainWndCallBack, PCEventObserver, PeerConnectSignalClientObserver
{
    RTCConductor(MainActivity mainWnd)
    {
    //    pc = new PeerConnectConductor(this);
        pcSignalClient = new PeerConnectSignalClient(this);
        this.mainWnd = mainWnd;
    }

    PeerConnectConductor pc = null;
    PeerConnectSignalClient pcSignalClient = null;
    MainActivity   mainWnd;

    public int StartLogin(String ip, int port)
    {
        pcSignalClient.Connect(/*"39.108.158.141"*/"192.168.0.108", 6000, "hello world");
        return  0;
    }

    public int ConnectToPeer(final Context context, int id, EglBase.Context renderEGLContext, SurfaceViewRenderer localRender, SurfaceViewRenderer remoteRender)
    {
        int nRet = 0;
        pc = new PeerConnectConductor(this);
        pc.SetRender(renderEGLContext, localRender, remoteRender);
        pc.ConnectToPeer(context, id);
        return nRet;
    }

    @Override
    public void OnEventOffer(final SessionDescription offer)
    {
       //send offer
        pcSignalClient.SendOfferSdp(offer, 0);
    }

    @Override
    public void OnEventAnswer(final SessionDescription answer)
    {
       //send answer
        pcSignalClient.SendAnswerSdp(answer);
    }

    @Override
    public void OnEventCandidate(final IceCandidate candidate)
    {
        //send candidate
        pcSignalClient.SendCandidate(candidate);
    }

    public void OnIceConnectionStateChanged(PeerConnection.IceConnectionState state)
    {

    }

    public void OnIceConnected()
    {
        mainWnd.OnIceConnected();
    }

    public void OnIceDisConnected()
    {
        mainWnd.OnIceDisConnected();
    }



    //PeerConnectSignalClientObserver implementation
    public void OnSignedIn()
    {

    }
    public void OnDisconnected()
    {

    }

    public void OnPeerConnected(List<Integer> listFriends)
    {
        assert(mainWnd != null);
        mainWnd.ShowFriends(listFriends);
    }
    public void OnPeerDisconnected()
    {

    }
    public void OnMessageSent()
    {

    }
    public void OnServerConnectionFailure()
    {

    }

    public  void OnPeerOffer(SessionDescription offer)
    {
       pc.SetRemoteDescription(offer);
    }

    public  void OnPeerAnswer(SessionDescription answer)
    {
       pc.SetRemoteDescription(answer);
    }

    public  void OnPeerCandidate(IceCandidate candidate)
    {
       pc.AddCandidate(candidate);
    }

    public  void OnMessageFromPeer(int peerId, String message)
    {

    }
}