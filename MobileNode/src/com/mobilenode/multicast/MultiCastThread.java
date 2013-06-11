package com.mobilenode.multicast;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import android.os.Handler;
import android.util.Log;

import com.mobilenode.main.VideoFeatures;

public class MultiCastThread implements Runnable
{
    MulticastSocket s;
    DatagramPacket pack;
    String rtspUrl;
    Handler handler;
	VideoFeatures myFeatures;
    public static final int PACKET_SENT = 7;
    
    public MultiCastThread(String rtspUrl, Handler handler, VideoFeatures myFeatures)
    {
    	this.rtspUrl = rtspUrl;
    	this.handler = handler;
    	this.myFeatures = myFeatures;
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
        	String data = "My Url is: " + rtspUrl + '\n' 
        			+ "The video Features are: \n" +
        			"  - Resolution: " + myFeatures.getVideoRes() + "px" + '\n' +
        			"  - Bit Rate: " + myFeatures.getBitRate() + "kbps" + '\n' +
        			"  - Frame Rate: " + myFeatures.getFrameRate() + "fps" + '\n';
            pack = new DatagramPacket(data.getBytes(),data.getBytes().length, InetAddress.getByName(WifiConstants.GROUP_ADDR), WifiConstants.PORT_NO);
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
