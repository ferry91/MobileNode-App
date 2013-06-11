package com.mobilenode.multicast;

import android.os.Handler;

import com.mobilenode.main.VideoFeatures;

public class MulticastManager implements Runnable{

	private String rtspUrl;
	private Handler handler;
	private VideoFeatures myFeatures;
	
	public MulticastManager (String rtspUrl, Handler handler, VideoFeatures myFeatures) {
		this.rtspUrl = rtspUrl;
		this.handler = handler;
		this.myFeatures = myFeatures;
    }
	
	@Override
	public void run(){
		Thread sendMulticast = new Thread(new MultiCastThread(rtspUrl, handler, myFeatures));
        sendMulticast.start();
	}
}
