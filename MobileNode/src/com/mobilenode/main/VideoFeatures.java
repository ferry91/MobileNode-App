package com.mobilenode.main;

import java.io.Serializable;

public class VideoFeatures implements Serializable{
	
	private int video_ResX;
	private int video_ResY;
	private String video_bitrate;
	private String video_framerate;
	
	public VideoFeatures(int video_ResX, int video_ResY, String video_bitrate, String video_framerate){
		this.video_ResX = video_ResX;
		this.video_ResY = video_ResY;
		this.video_bitrate = video_bitrate;
		this.video_framerate = video_framerate; 
	}
	
	public String getVideoRes(){
		String res = this.video_ResX + "x" + this.video_ResY;
		return res;
	}
	
	public String getBitRate(){
		return this.video_bitrate;
	}
	
	public String getFrameRate(){
		return this.video_framerate;
	}
}
