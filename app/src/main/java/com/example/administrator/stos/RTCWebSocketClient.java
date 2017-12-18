package com.example.administrator.stos;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

interface RTCWebSocketHandler
{
    public abstract void OnOpen(ServerHandshake var1);
    public abstract void OnClose();
    public abstract void OnMessage(String message);
    public abstract void OnError();
}

public class RTCWebSocketClient extends WebSocketClient
{
    public RTCWebSocketClient(URI serverUri/*, Draft draft*/)
    {
        super(serverUri/*, draft*/);
    }

    public void RegisterWebSoocketHandler(RTCWebSocketHandler handler)
    {
        if (null == webSocketClientHandler)
        {
            webSocketClientHandler = handler;
        }
    }

    private RTCWebSocketHandler  webSocketClientHandler = null;
    @Override
    public void onOpen(ServerHandshake var1)
    {
        webSocketClientHandler.OnOpen(var1);
    }
    @Override
    public void onMessage(String var1)
    {
        webSocketClientHandler.OnMessage(var1);
    }
    @Override
    public void onClose(int var1, String var2, boolean var3)
    {
        webSocketClientHandler.OnClose();
    }
    @Override
    public void onError(Exception var1)
    {
        webSocketClientHandler.OnError();
    }
}