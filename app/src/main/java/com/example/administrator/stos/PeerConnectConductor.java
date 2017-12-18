package com.example.administrator.stos;

import android.util.Log;
import android.content.Context;

import junit.framework.Assert;

import org.json.JSONObject;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoTrack;
import org.webrtc.VideoSource;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.EglBase;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.Logging;

import junit.framework.*;

import java.util.EnumSet;
import java.util.LinkedList;
import org.json.*;

interface PCEventObserver
{
    public abstract void OnEventOffer(final SessionDescription offer);
    public abstract void OnEventAnswer(final SessionDescription answer);
    public abstract void OnEventCandidate(final IceCandidate candidate);
    public abstract void OnIceConnectionStateChanged(PeerConnection.IceConnectionState state);
    public abstract void OnIceConnected();
    public abstract void OnIceDisConnected();

}

class ConstParams
{
    //SessionDescription
    static final String kSessionDescriptionTypeName = "type";
    static final String kSessionDescriptionSdpName = "sdp";

    //IceCandidate
    static final String kCandidateSdpMidName = "sdpMid";
    static final String kCandidateSdpMlineIndexName = "sdpMLineIndex";
    static final String kCandidateSdpName = "candidate";
}

public class PeerConnectConductor implements PeerConnection.Observer,SdpObserver,CameraVideoCapturer.CameraEventsHandler
{
    public PeerConnectConductor(PCEventObserver peo)
    {
        eventObserver = peo;
    }

    //callback event obj
    PCEventObserver eventObserver;

    private PeerConnectionFactory pcFactory;
    private PeerConnection  pc = null;
    private int peer_id_ = -1;
    private PeerConnectionFactory.Options ops;
    private MediaConstraints pcConstraints;
    private MediaConstraints videoConstrains;
    private MediaConstraints audioConstrains;
    private MediaConstraints sdpMediaConstrains;
    Context context;

    public  LinkedList<PeerConnection.IceServer> iceServers;

    private MediaStream mediaStream;
    private VideoCapturerAndroid videoCapturer;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private AudioTrack localAudioTrack;
    private AudioTrack remoteAudioTrack;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private SessionDescription   localDescription;
    private SessionDescription   remoteDescription;
    private EglBase.Context renderEGLContext;
    private boolean  isInitiator;

    //const value
    private static final int HD_VIDEO_WIDTH = 720;
    private static final int HD_VIDEO_HEIGHT = 480;
    private static final int VIDEO_FPS = 25;

    private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT= "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT  = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

    public void SetRender(EglBase.Context renderEGLContext, VideoRenderer.Callbacks localRender, VideoRenderer.Callbacks remoteRender)
    {
        this.renderEGLContext = renderEGLContext;
        this.localRender = localRender;
        this.remoteRender = remoteRender;
    }

    public void ConnectToPeer(final Context context, int peer_id)
    {
        assert(peer_id_ == -1);
        assert(peer_id >= 0);
        this.context = context;

        if (true == InitPeerConnect())
        {
            pc.createOffer(this, sdpMediaConstrains);
            isInitiator = true;
        }
    }

    public boolean InitPeerConnect()
    {
        //Create peer connection factory
        if (!PeerConnectionFactory.initializeAndroidGlobals(this.context, true, true, false))
        {
            Log.d("PeerConnectConductor", "Create peer connection factory failed.");
            return false;
        }

        PeerConnectionFactory.initializeInternalTracer();
        PeerConnectionFactory.initializeFieldTrials("");

        ops = new PeerConnectionFactory.Options();
        ops.networkIgnoreMask = 0;
       // ops.disableEncryption = true;
       // ops.disableNetworkMonitor = true;

        pcFactory = new PeerConnectionFactory(ops);
        if (pcFactory == null)
        {
            Log.d("PeerConnectConductor", "Create peer connection factory failed.");
            return false;
        }

        CreatePeerConnection();
        AddStreams();
        return pc.getClass() != null;
    }

    public void CreatePeerConnection()
    {
        pcConstraints = new MediaConstraints();
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        iceServers = new LinkedList<PeerConnection.IceServer>();
        PeerConnection.IceServer ics = new PeerConnection.IceServer("stun:stun.l.google.com:19302");
        iceServers.add(ics);

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        pc = pcFactory.createPeerConnection(rtcConfig, pcConstraints, this);

        // Set default WebRTC tracing and INFO libjingle logging.
        // NOTE: this _must_ happen while |factory| is alive!
        Logging.enableTracing("logcat:", EnumSet.of(Logging.TraceLevel.TRACE_ERROR));
      }

    public void AddStreams()
    {
       if (mediaStream != null)
       {
           Log.d("PeerConnectConductor", "media stream is already created.");
           return;
       }

        mediaStream = pcFactory.createLocalMediaStream("ARDAMS");

        boolean videoEnabled = true;
        int numberOfCamera = CameraEnumerationAndroid.getDeviceCount();
        if (numberOfCamera == 0)
        {
            Log.w("PeerConnectConductor", "no camera device can be used.");
            videoEnabled = false;
        }

        if (videoEnabled)
        {
            pcFactory.setVideoHwAccelerationOptions(renderEGLContext, renderEGLContext);
            videoConstrains = new MediaConstraints();
            int videoWidth = HD_VIDEO_WIDTH;
            int videoHeight = HD_VIDEO_HEIGHT;
            int videoFps = VIDEO_FPS;

            if (videoWidth > 0 && videoHeight > 0)
            {
                videoConstrains.mandatory.add(new MediaConstraints.KeyValuePair("minWidth", Integer.toString(videoWidth)));
                videoConstrains.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(videoWidth)));
                videoConstrains.mandatory.add(new MediaConstraints.KeyValuePair("minHeight", Integer.toString(videoHeight)));
                videoConstrains.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(videoHeight)));
            }

            if (videoFps > 0)
            {
                videoConstrains.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(videoFps)));
                videoConstrains.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(videoFps)));
            }
        }

        audioConstrains = new MediaConstraints();
        audioConstrains.mandatory.add(new MediaConstraints.KeyValuePair(
                AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstrains.mandatory.add(new MediaConstraints.KeyValuePair(
                AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "true"));
        audioConstrains.mandatory.add(new MediaConstraints.KeyValuePair(
                AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));
        audioConstrains.mandatory.add(new MediaConstraints.KeyValuePair(
                AUDIO_NOISE_SUPPRESSION_CONSTRAINT , "true"));

        // Create SDP constraints.
        sdpMediaConstrains = new MediaConstraints();
        sdpMediaConstrains.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstrains.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        String cameraDeviceName = CameraEnumerationAndroid.getDeviceName(0);
        String frontCameraDeviceName =
                CameraEnumerationAndroid.getNameOfFrontFacingDevice();
        if (frontCameraDeviceName != null) {
            cameraDeviceName = frontCameraDeviceName;
        }

        videoCapturer = VideoCapturerAndroid.create(cameraDeviceName, this);
        if (videoCapturer == null)
        {
            return;
        }

        mediaStream.addTrack(createVideoTrack(videoCapturer));
        mediaStream.addTrack(createAudioTrack());
        pc.addStream(mediaStream);
        videoCapturer.startCapture(HD_VIDEO_WIDTH, HD_VIDEO_HEIGHT, VIDEO_FPS);
    }

    private VideoTrack createVideoTrack(VideoCapturerAndroid capturer) {
        videoSource = pcFactory.createVideoSource(capturer);

        localVideoTrack = pcFactory.createVideoTrack("ARDAMSv0", videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addRenderer(new VideoRenderer(localRender));
        return localVideoTrack;
    }

    private AudioTrack createAudioTrack()
    {
        localAudioTrack = pcFactory.createAudioTrack(
                "ARDAMSa0",
                pcFactory.createAudioSource(audioConstrains));
        localAudioTrack.setEnabled(true);
        return localAudioTrack;
    }

    public void SetRemoteDescription(SessionDescription sd)
    {
        remoteDescription = sd;
        pc.setRemoteDescription(this, sd);

        if (true == InitPeerConnect())
        {
       //     pc.createAnswer(this, sdpMediaConstrains);
            isInitiator = false;
        }

    }

    public void AddCandidate(IceCandidate candidate)
    {
        pc.addIceCandidate(candidate);
    }


    //peerconnectObsever implementation
    @Override
    public void onSignalingChange(PeerConnection.SignalingState var1)
    {
           if (var1 == PeerConnection.SignalingState.HAVE_LOCAL_OFFER)
           {
              Log.i("onSignalingChange", "have local offer.");
           }
           else if (var1 == PeerConnection.SignalingState.HAVE_REMOTE_OFFER)
           {
               Log.i("onSignalingChange", "have remote offer.");
           }
           else if (var1 == PeerConnection.SignalingState.STABLE)
           {
               Log.i("onSignalingChange", "stable.");
           }
    }

    //callback after ICE begin, when createoffer is over.
    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState var1)
    {
        //callback ice connect state
        PeerConnection.IceConnectionState newState = var1;
        //    Log.d(TAG, "IceConnectionState: " + newState);
        if (newState == PeerConnection.IceConnectionState.CONNECTED)
        {
            eventObserver.OnIceConnected();
        }
        else if (newState == PeerConnection.IceConnectionState.DISCONNECTED)
        {
            eventObserver.OnIceDisConnected();
        }
        else if (newState == PeerConnection.IceConnectionState.FAILED)
        {
            Log.e("onIceConnectionChange", "IceConnectionState: " + newState);
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean var1)
    {

    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState var1)
    {

    }

    @Override
    public void onIceCandidate(IceCandidate var1)
    {
       //called after setlocaloffer
        eventObserver.OnEventCandidate(var1);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] var1)
    {

    }

    @Override
    public void onAddStream(MediaStream var1)
    {
        //add remote stream
        if (pc == null)
        {
           return;
        }

        if (var1.audioTracks.size() > 1 || var1.videoTracks.size() > 1)
        {
            Log.i("onAddStream", "Weird-looking stream: " + var1);
            return;
        }

        if (var1.videoTracks.size() == 1)
        {
            remoteVideoTrack = var1.videoTracks.get(0);
            remoteVideoTrack.setEnabled(true);
            remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
        }

     //   pc.addStream(var1);
        Log.i("onAddStream", "add remote stream");
    }

    @Override
    public void onRemoveStream(MediaStream var1)
    {
         remoteVideoTrack = null;
    }

    @Override
    public void onDataChannel(DataChannel var1)
    {

    }

    @Override
    public void onRenegotiationNeeded()
    {
        //重新协商信令
        //callback after addStream() is invoked.
        Log.i("onRenegotiationNeeded", "onRenegotiationNeeded");
    }

    //Sdp Observer
    @Override
    public void onCreateSuccess(SessionDescription var1)
    {
        //create offer success.
        //setlocalDescription
        if (var1.type == SessionDescription.Type.OFFER)
        {
             localDescription = new SessionDescription(var1.type, var1.description);
             pc.setLocalDescription(this, localDescription);
        }
        else if (var1.type == SessionDescription.Type.ANSWER)
        {
            localDescription = new SessionDescription(var1.type, var1.description);
            pc.setLocalDescription(this, localDescription);
        }

    }

    @Override
    public void onSetSuccess()
    {
       //set success and sendMessage
        if (isInitiator)
        {
            //offer to peer
            if (pc.getRemoteDescription() == null)
            {
                eventObserver.OnEventOffer(localDescription);
            }
            else
            {
                 Log.i("PeerConnectConductor", "set remote description success.");
            }
        }
        else
        {
            //answer to peer
            if (pc.getLocalDescription() != null)
            {
                eventObserver.OnEventAnswer(localDescription);
            }
            else
            {
                Log.i("PeerConnectConductor", "set remote description success.");
                pc.createAnswer(this, sdpMediaConstrains);
            }
        }

    }

    @Override
    public void onCreateFailure(String var1)
    {
        Log.e("onCreateFailure", "create offer failed");
    }

    @Override
    public void onSetFailure(String var1)
    {
        Log.e("onSetFailure", "set offer or answer failed");
    }

    //camera
    @Override
    public void onCameraError(String var1)
    {
       String err = var1;
    }

    @Override
    public void onCameraFreezed(String var1)
    {
        String state = var1;
    }

    @Override
    public void onCameraOpening(String var1)
    {
        String state = var1;
    }

    @Override
    public void onFirstFrameAvailable()
    {
        String state = "first frame";
    }

    @Override
    public void onCameraClosed()
    {
        String state = "closed";
    }
}