package com.mobilenode.multicast;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import android.os.Handler;
import android.util.Log;

public class MultiCastThread implements Runnable
{
    MulticastSocket s;
    DatagramPacket pack;
    String rtspUrl;
    Handler handler;
    public static final int PACKET_SENT = 7;
    
    public MultiCastThread(String rtspUrl, Handler handler)
    {
    	this.rtspUrl = rtspUrl;
    	this.handler = handler;
        try
        {
            s = new MulticastSocket(WifiConstants.PORT_NO);
            s.joinGroup(InetAddress.getByName(WifiConstants.GROUP_ADDR));
        }
        catch(Exception e)
        {
            Log.v("Socket Error: ",e.getMessage());
        }
    }
    @Override
    public void run()
    {
        try
        {
            pack = new DatagramPacket(rtspUrl.getBytes(),rtspUrl.getBytes().length, InetAddress.getByName(WifiConstants.GROUP_ADDR), WifiConstants.PORT_NO);
            s.setTimeToLive(WifiConstants.TIME_TO_LIVE);
            s.send(pack);
            handler.obtainMessage(PACKET_SENT).sendToTarget();
        }
        catch(Exception e)
        {
            Log.v("Packet Sending Error: ",e.getMessage());
        }
    }
}
