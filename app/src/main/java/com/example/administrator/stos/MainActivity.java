package com.example.administrator.stos;

import android.Manifest;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.content.Context;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.content.pm.PackageManager;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import com.example.administrator.stos.R;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.EglBase;


import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

interface MainWndCallBack
{
    public abstract int StartLogin(String ip, int port);
    public abstract int ConnectToPeer(final Context context, int id, EglBase.Context renderEGLContext, SurfaceViewRenderer localRender, SurfaceViewRenderer remoteRender);
}

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_ROOMID =
            "org.appspot.apprtc.ROOMID";
    public static final String EXTRA_LOOPBACK =
            "org.appspot.apprtc.LOOPBACK";
    public static final String EXTRA_VIDEO_CALL =
            "org.appspot.apprtc.VIDEO_CALL";
    public static final String EXTRA_VIDEO_WIDTH =
            "org.appspot.apprtc.VIDEO_WIDTH";
    public static final String EXTRA_VIDEO_HEIGHT =
            "org.appspot.apprtc.VIDEO_HEIGHT";
    public static final String EXTRA_VIDEO_FPS =
            "org.appspot.apprtc.VIDEO_FPS";
    public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED =
            "org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER";
    public static final String EXTRA_VIDEO_BITRATE =
            "org.appspot.apprtc.VIDEO_BITRATE";
    public static final String EXTRA_VIDEOCODEC =
            "org.appspot.apprtc.VIDEOCODEC";
    public static final String EXTRA_HWCODEC_ENABLED =
            "org.appspot.apprtc.HWCODEC";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED =
            "org.appspot.apprtc.CAPTURETOTEXTURE";
    public static final String EXTRA_AUDIO_BITRATE =
            "org.appspot.apprtc.AUDIO_BITRATE";
    public static final String EXTRA_AUDIOCODEC =
            "org.appspot.apprtc.AUDIOCODEC";
    public static final String EXTRA_NOAUDIOPROCESSING_ENABLED =
            "org.appspot.apprtc.NOAUDIOPROCESSING";
    public static final String EXTRA_AECDUMP_ENABLED =
            "org.appspot.apprtc.AECDUMP";
    public static final String EXTRA_OPENSLES_ENABLED =
            "org.appspot.apprtc.OPENSLES";
    public static final String EXTRA_DISPLAY_HUD =
            "org.appspot.apprtc.DISPLAY_HUD";
    public static final String EXTRA_TRACING = "org.appspot.apprtc.TRACING";
    public static final String EXTRA_CMDLINE =
            "org.appspot.apprtc.CMDLINE";
    public static final String EXTRA_RUNTIME =
            "org.appspot.apprtc.RUNTIME";



    List<Integer> listData = new LinkedList<Integer>();
    List<Integer> friendsList = new LinkedList<Integer>();

    private ListView listView;
    private EglBase rootEglBase;
    private Context context;
    boolean iceConnected;
    ScalingType scalingType;
    String peerid;


    // Peer connection statistics callback period in ms.
    private static final int STAT_CALLBACK_PERIOD = 1000;
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 480;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 2;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private SurfaceViewRenderer localRender;
    private SurfaceViewRenderer remoteRender;
    private PercentFrameLayout  localRenderLayout;
    private PercentFrameLayout  remoteRenderLayout;

    private VideoRenderer localGuiVideoRender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(new ArrayAdapter<Integer>(this,
                android.R.layout.simple_list_item_1,
                listData));

        iceConnected = false;
        peerid = "";
        scalingType = ScalingType.SCALE_ASPECT_FILL;
    /*    GLSurfaceView videoView = (GLSurfaceView)findViewById(R.id.local_gl_video_view);
        VideoRendererGui.setView(videoView, new Runnable() {
            @Override
            public void run() {
                String strRender = "local render";
            }
        });*/

    /*    try
        {
            localGuiVideoRender = VideoRendererGui.createGui(0, 0, 100, 100, scalingType, true);
        }
        catch (Exception e)
        {

        }*/

        localRender = (SurfaceViewRenderer)findViewById(R.id.local_gl_video_view);
        remoteRender = (SurfaceViewRenderer)findViewById(R.id.remote_gl_video_view);
        localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
        remoteRenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);

        rtc.StartLogin("", 80);

        listView.setOnItemClickListener(
                    new AdapterView.OnItemClickListener(){
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            setTitle("您点击了第"+position+"个项目");
                            peerid = listView.getItemAtPosition(position).toString();
                        }
                    }
        );

        // Check for mandatory permissions.
      /*  for (String permission : MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
         //       logAndToast("Permission " + permission + " is not granted");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }*/

        // Create video renderers.
        rootEglBase = EglBase.create();
        localRender.init(rootEglBase.getEglBaseContext(), null);
        remoteRender.init(rootEglBase.getEglBaseContext(), null);
        localRender.setKeepScreenOn(true);
        remoteRender.setKeepScreenOn(true);
        localRender.setZOrderOnTop(true);

        localRender.setZOrderMediaOverlay(true);
        remoteRender.setZOrderMediaOverlay(true);
        updateVideoView();

    }

    @Override
    protected void onDestroy()
    {
        rootEglBase.release();
        super.onDestroy();
    }

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO",
            "android.permission.INTERNET"
    };


    private void updateVideoView()
    {
         remoteRender.setX(REMOTE_X);
         remoteRender.setY(REMOTE_Y);
         remoteRender.setScalingType(scalingType);
         remoteRender.setMirror(true);

         if(iceConnected)
         {
             localRender.setX(LOCAL_X_CONNECTING);
             localRender.setY(LOCAL_Y_CONNECTING);
             localRender.setScalingType(ScalingType.SCALE_ASPECT_FIT);
         }
         else
         {
             localRender.setX(LOCAL_X_CONNECTING);
             localRender.setY(LOCAL_Y_CONNECTING);
             scalingType = ScalingType.SCALE_ASPECT_FIT;
             localRender.setScalingType(scalingType);
         }

         localRender.setMirror(true);
         localRender.requestLayout();
         remoteRender.requestLayout();
    }

    int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 66;
    private MainWndCallBack  rtc = new RTCConductor(this);

    public void button_click(View source)
    {
        peerid = "2";
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(MainActivity.this,
                                                                   Manifest.permission.CAMERA))
        {
            //has permission, do operation directly
            Log.i("DEBUG_TAG", "user has the permission already!");
            rtc.ConnectToPeer(MainActivity.this, Integer.parseInt(peerid),  rootEglBase.getEglBaseContext(),
                    localRender, remoteRender);
        }
        else
        {
            //do not have permission
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.CAMERA))
            {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Log.i("DEBUG_TAG", "we should explain why we need this permission!");
            }
            else
            {
                // No explanation needed, we can request the permission.
                Log.i("DEBUG_TAG", "==request the permission==");

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET,
                                Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.ACCESS_NETWORK_STATE},
                        MY_PERMISSIONS_REQUEST_READ_CONTACTS);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }

        if (peerid.isEmpty())
        {
            return;
        }

        setTitle("您点击了第"+peerid+"个项目");
  //      rtc.ConnectToPeer(MainActivity.this, Integer.parseInt(peerid),  rootEglBase.getEglBaseContext(),
  //                         localRender, remoteRender);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode)
        {
            case 66:
                {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    //     ContactsUtils.readPhoneContacts(this);
                    Log.i("DEBUG_TAG", "user granted the permission!");
                    rtc.ConnectToPeer(MainActivity.this, Integer.parseInt(peerid),  rootEglBase.getEglBaseContext(),
                            localRender, remoteRender);
                }
                else
                {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.i("DEBUG_TAG", "user denied the permission!");
                }
                return;
            }
        }
    }

    public void ShowFriends(List<Integer> listFriends)
    {
        for (int i = 0; i < listFriends.size(); ++i)
        {
            int id = listFriends.get(i);
            listData.add(id);
        }

    /*    listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(new ArrayAdapter<Integer>(this,
                android.R.layout.simple_list_item_1,
                friendsList));*/
        listView.refreshDrawableState();

  //      updateVideoView();
    }

    public void OnIceConnected()
    {
        iceConnected = true;
    }

    public void OnIceDisConnected()
    {
        iceConnected = false;
    }


}
