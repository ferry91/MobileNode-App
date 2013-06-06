package com.mobilenode.multicast;

import android.os.Handler;

public class MulticastManager implements Runnable{

	private String rtspUrl;
	private Handler handler;
	
	public MulticastManager (String rtspUrl, Handler handler) {
		this.rtspUrl = rtspUrl;
		this.handler = handler;
    }
	
	@Override
	public void run(){
		Thread sendMulticast = new Thread(new MultiCastThread(rtspUrl, handler));
        sendMulticast.start();
	}
}
