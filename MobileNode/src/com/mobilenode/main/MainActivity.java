/*
 * Copyright (C) 2011 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.mobilenode.main;

import java.io.IOException;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.Camera;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.mobilenode.main.RtspServer.WorkerThread;
import com.mobilenode.multicast.MultiCastThread;
import com.mobilenode.multicast.MulticastManager;
import com.mobilenode.streaming.video.VideoQuality;

/** 
 * MixingWaves launches an RtspServer, clients can then connect to it and receive audio/video streams from the phone
 */
public class MainActivity extends Activity implements OnSharedPreferenceChangeListener {
    
    static final public String TAG = "MainActivity";
    //public static final int MAX_LOG_LINE = 3; 
    
    private HttpServer httpServer = null;
    private ImageView logo;
    private PowerManager.WakeLock wl;
    private RtspServer rtspServer = null;
    private SurfaceHolder holder;
    private SurfaceView camera;
    private TextView console, ip;
    private VideoQuality defaultVideoQuality = new VideoQuality();
    public static Button button;
    private Camera cam;
    private int port;
  	private WifiManager wifiManager;
  	private WifiInfo wifiInfo;
  	private WifiManager.MulticastLock lock;
    
    private static FFMPEGWrapper ffmpeg;
    
    public void onCreate(Bundle savedInstanceState) {
    	this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        
        
        setContentView(R.layout.main);
        
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
    	wifiInfo = wifiManager.getConnectionInfo();
    	
    	if(wifiManager != null)
        {
            lock = wifiManager.createMulticastLock("WifiDevices");
            lock.acquire();
        }
    	port = 8086;
    	//Send rtsp URL to the server
    	String rtspUrl = generateUrl(wifiInfo);
    	Thread mManager = new Thread(new MulticastManager(rtspUrl, handler));
        mManager.start();
    	
        camera = (SurfaceView)findViewById(R.id.smallcameraview);
        logo = (ImageView)findViewById(R.id.logo);
        console = (TextView) findViewById(R.id.console);
        ip = (TextView) findViewById(R.id.ip);
        
        
     /*   resolutionArray = getResources().getStringArray(R.array.videoResolutionArray);
        Parameters params = cam.getParameters();
        List<Camera.Size> sizes = params.getSupportedPictureSizes();
        for(int i = 0; i < sizes.size(); i++){
        	int height = sizes.get(i).height;
        	int width = sizes.get(i).width;
        	resolutionArray[i] = String.valueOf(width) + "x" + String.valueOf(height);
        }*/
        
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        defaultVideoQuality.resX = settings.getInt("video_resX", 640);
        defaultVideoQuality.resY = settings.getInt("video_resY", 480);
        defaultVideoQuality.frameRate = Integer.parseInt(settings.getString("video_framerate", "15"));
        defaultVideoQuality.bitRate = Integer.parseInt(settings.getString("video_bitrate", "500"))*1000; // 500 kb/s
        
        settings.registerOnSharedPreferenceChangeListener(this);
       	
        camera.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holder = camera.getHolder();
		
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "net.majorkernelpanic.spydroid.wakelock");
    
    	// Print version number
        try {
			log("<b>" + getString(R.string.app_name) + " v" + this.getPackageManager().getPackageInfo(this.getPackageName(), 0 ).versionName + "</b>");
		} catch (NameNotFoundException e) {
			log("<b>" + getString(R.string.app_name) + "</b>");
		}
        
        Session.setSurfaceHolder(holder);
        Session.setDefaultVideoQuality(defaultVideoQuality);
        Session.setDefaultAudioEncoder(settings.getBoolean("stream_audio", true)?Integer.parseInt(settings.getString("audio_encoder", "1")):0);
        Session.setDefaultVideoEncoder(settings.getBoolean("stream_video", true)?Integer.parseInt(settings.getString("video_encoder", "1")):0);
        addListenerOnButton();
        if (settings.getBoolean("enable_rtsp", true))
			try {
				rtspServer = new RtspServer(port, handler, button);
			} catch (IOException e) {
				e.printStackTrace();
			}
        if (settings.getBoolean("enable_http", true)) httpServer = new HttpServer(8080, this.getAssets(), handler);
		//camera.setBackgroundDrawable(null);
        //sendTweet(wifiInfo);
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	if (key.equals("video_resX")) {
    		defaultVideoQuality.resX = sharedPreferences.getInt("video_resX", 640);
    		Session.setDefaultVideoQuality(defaultVideoQuality);
    	}
    	else if (key.equals("video_resY"))  {
    		defaultVideoQuality.resY = sharedPreferences.getInt("video_resY", 480);
    		Session.setDefaultVideoQuality(defaultVideoQuality);
    	}
    	else if (key.equals("video_framerate")) {
    		defaultVideoQuality.frameRate = Integer.parseInt(sharedPreferences.getString("video_framerate", "15"));
    		Session.setDefaultVideoQuality(defaultVideoQuality);
    	}
    	else if (key.equals("video_bitrate")) {
    		defaultVideoQuality.bitRate = Integer.parseInt(sharedPreferences.getString("video_bitrate", "500"))*1000;
    		Session.setDefaultVideoQuality(defaultVideoQuality);
    	}
    	else if (key.equals("stream_audio") || key.equals("audio_encoder")) { 
    		Session.setDefaultAudioEncoder(sharedPreferences.getBoolean("stream_audio", true)?Integer.parseInt(sharedPreferences.getString("audio_encoder", "1")):0);
    	}
    	else if (key.equals("stream_video") || key.equals("video_encoder")) {
    		Session.setDefaultVideoEncoder(sharedPreferences.getBoolean("stream_video", true)?Integer.parseInt(sharedPreferences.getString("video_encoder", "1")):0);
    	}
    	else if (key.equals("enable_http")) {
    		if (sharedPreferences.getBoolean("enable_http", true)) {
    			httpServer =  new HttpServer(8080, this.getAssets(), handler);
    		} else {
    			if (httpServer != null) httpServer = null;
    		}
    	}
    	else if (key.equals("enable_rtsp")) {
    		if (sharedPreferences.getBoolean("enable_rtsp", true)) {
    			try {
					rtspServer =  new RtspServer(port, handler, button);
				} catch (IOException e) {
					e.printStackTrace();
				}
    		} else {
    			if (rtspServer != null) rtspServer = null;
    		}
    	}	
    }
    
    public void addListenerOnButton() {
		 
		button = (Button) findViewById(R.id.stopButton);
		button.setVisibility(View.INVISIBLE);
		button.setEnabled(false); //Disabled by default
 
		button.setOnClickListener(new OnClickListener() {
			//Stop all current sessions
			@Override
			public void onClick(View arg0) {
				rtspServer.setInterrupted();
			}
 
		});
	}
        
    public void onStart() {
    	super.onStart();
    	// Lock screen
    	wl.acquire();
    }
    
    public void onStop() {
    	super.onStop();
    	wl.release();
    }
    
    public void onResume() {
    	super.onResume();
    	
    	// Determines if user is connected to a wireless network & displays ip 
    	wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
    	wifiInfo = wifiManager.getConnectionInfo();
    	displayIpAddress(wifiInfo);
    	
    	
    	startServers();
    	
    	registerReceiver(wifiStateReceiver,new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
    	
    	// Runnable for FFMPEG    
    	Runnable runProcessVideo = new Runnable () {
    		public void run ()
    		{
    			try
    			{
					if (ffmpeg == null)
					{
						ffmpeg = new FFMPEGWrapper(MainActivity.this.getBaseContext());
					}
					
    				String url = "rtsp://" + "127.0.0.1" + ":" + port + "/?h264=500-30-720-480";
    				ffmpeg.processVideo(url);
    				//log("I get");
    					
    			}
    			catch (Exception e)
    			{
    				Log.e(TAG,"error with ffmpeg",e);
    			}
    		}
    	};   
    		
		// Create Thread for FFMPEG
    	Thread thread = new Thread(runProcessVideo);
    	//thread.setPriority(Thread.MAX_PRIORITY);
    	thread.start();    	
    	
    }
    
    public void onPause() {
    	super.onPause();
    	stopServers();
    	unregisterReceiver(wifiStateReceiver);
    }
    
    private void stopServers() {
    	if (rtspServer != null) rtspServer.stop();
    	if (httpServer != null) httpServer.stop();
    }
    
    private void startServers() {
    	if (rtspServer != null) {
    		try {
    			rtspServer.start();
    		} catch (IOException e) {
    			log("RtspServer could not be started : "+e.getMessage());
    		}
    	}
    	if (httpServer != null) {
    		try {
    			httpServer.start();
    		} catch (IOException e) {
    			log("HttpServer could not be started : "+e.getMessage());
    		}
    	}
    }
    
    // BroadcastReceiver that detects wifi state changements
    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
        	String action = intent.getAction();
        	if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
        		WifiInfo wifiInfo = (WifiInfo)intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
        		Log.d(TAG,"Wifi state has changed ! null?: "+(wifiInfo==null));
        		// Seems like wifiInfo is ALWAYS null on android 2
        		if (wifiInfo != null) {
        			Log.d(TAG,wifiInfo.toString());
        			displayIpAddress(wifiInfo);
        		}
        		else {
        	    	WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        	    	WifiInfo info = wifiManager.getConnectionInfo();
        	    	displayIpAddress(info);
        		}
        	}
        } 
    };
    
    // The Handler that gets information back from the RtspServer
    private final Handler handler = new Handler() {
    	
    	public void handleMessage(Message msg) {
    		
    		switch (msg.what) {
    			
    		case RtspServer.MESSAGE_LOG:
    			// Sent when the streamingManager has something to report
    			log((String)msg.obj);
    			break;

    		case Session.MESSAGE_START:
    			// Sent when streaming starts
    			logo.setAlpha(100);
    			camera.setBackgroundDrawable(null);
    			break;
    			
    		case Session.MESSAGE_STOP:
    			// Sent when streaming ends
    			camera.setBackgroundResource(R.drawable.background3);
    			logo.setAlpha(255);
    			break;
    			
    		case WorkerThread.BUTTON_VISIBLE:
    			button.setVisibility(View.VISIBLE);
        		button.setEnabled(true);
        		break;
        		
    		case WorkerThread.BUTTON_INVISIBLE:
    			button.setVisibility(View.INVISIBLE);
        		button.setEnabled(false);
        		break;
    		
    		case MultiCastThread.PACKET_SENT:
    			lock.release();
    			break;
    		}	
    		
    	}
    	
    };
    
    
    private void displayIpAddress(WifiInfo wifiInfo) {
    	if (wifiInfo!=null && wifiInfo.getNetworkId()>-1) {
    		ip.setText(generateUrl(wifiInfo));
    	} else {
    		ip.setText("Wifi should be enabled !");
    	}
    }
    
    private String generateUrl(WifiInfo wifiInfo){
    	int i = wifiInfo.getIpAddress();
    	String rtspUrl = "rtsp://" + String.format("%d.%d.%d.%d", i & 0xff, i >> 8 & 0xff,i >> 16 & 0xff,i >> 24 & 0xff) + ":" + port + "/";
    	return rtspUrl;
    	
    }
    
    public void log(String s) {
    	String t = console.getText().toString();
    	/*int linecount = console.getLineCount();
    	if ( linecount >= MAX_LOG_LINE){
    		console.getEditableText().delete(1, linecount-1);
    	}    	*/
    	if (t.split("\n").length>8) {
    		console.setText(t.substring(t.indexOf("\n")+1, t.length()));
    	}
    	console.append(Html.fromHtml(s+"<br />"));
    }
    
    
}