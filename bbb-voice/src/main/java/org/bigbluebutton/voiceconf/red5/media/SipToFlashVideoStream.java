/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
* 
* Copyright (c) 2012 BigBlueButton Inc. and by respective authors (see below).
*
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License as published by the Free Software
* Foundation; either version 3.0 of the License, or (at your option) any later
* version.
* 
* BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License along
* with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
*
*/
package org.bigbluebutton.voiceconf.red5.media;

import org.red5.server.api.scope.IScope;
import java.net.DatagramSocket;
import org.red5.server.net.rtmp.event.VideoData;
import org.bigbluebutton.voiceconf.red5.media.transcoder.TranscodedMediaDataListener;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IScope;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.scope.Scope;
import org.red5.server.stream.IProviderService;
import org.bigbluebutton.voiceconf.red5.media.SipToFlashStream;

import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

import org.bigbluebutton.voiceconf.red5.media.transcoder.VideoProtocolConverter.RTMPPacketInfo;
import org.bigbluebutton.voiceconf.red5.media.transcoder.VideoProtocolConverter;
import org.bigbluebutton.voiceconf.red5.media.transcoder.ConverterObserver;
import org.bigbluebutton.voiceconf.red5.media.net.RtpPacket;


public class SipToFlashVideoStream implements SipToFlashStream, RtpStreamReceiverListener, ConverterObserver {
	private static final Logger log = Red5LoggerFactory.getLogger(SipToFlashAudioStream.class, "sip");

	private BroadcastStream videoBroadcastStream;
	private IScope scope;
	private final String freeswitchToBbbVideoStreamName;
	private RtpVideoStreamReceiver rtpStreamReceiver;
	private StreamObserver observer;

	private boolean sentMetadata = false;
	private IoBuffer videoBuffer;

	private VideoData videoData;

	private VideoProtocolConverter converter;
	private final int EXPECTED_MAXIMUM_PAYLOAD_LENGTH = 2048;


	private final byte[] fakeMetadata = new byte[] {
		0x02, 0x00 , 0x0a , 0x6f,0x6e,0x4d,0x65,0x74,0x61,0x44,0x61,0x74,0x61,0x8,0x00, 0x00, 0x00, 0x00,0x00, 0x0C,0x6d,0x6f,0x6f,0x76,0x50,0x6f,0x73,0x69,0x74,0x69,0x6f,0x6e,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x00, 0x05,0x77,0x69,0x64,0x74,0x68,0x0,0x40,(byte) 0x84,0x0,0x0,0x0,0x0,0x0,0x0,0x00, 0x06,0x68,0x65,0x69,0x67,0x68,0x74,0x0,0x40,0x7e,0x0,0x0,0x0,0x0,0x0,0x0
	};


	public SipToFlashVideoStream(IScope scope, VideoProtocolConverter converter, DatagramSocket socket) {
		this.scope = scope;		

		rtpStreamReceiver = new RtpVideoStreamReceiver(socket, EXPECTED_MAXIMUM_PAYLOAD_LENGTH);
		rtpStreamReceiver.setRtpStreamReceiverListener(this);

		freeswitchToBbbVideoStreamName = "freeswitchToBbbVideoStream_" + System.currentTimeMillis();

		videoBuffer = IoBuffer.allocate(EXPECTED_MAXIMUM_PAYLOAD_LENGTH*100);
		videoBuffer = videoBuffer.setAutoExpand(true);

		videoData = new VideoData();

		this.converter = converter;
		converter.setConverterObserver(this);
	}


	@Override
	public String getStreamName() {
		return freeswitchToBbbVideoStreamName;
	}

	@Override
	public void addListenStreamObserver(StreamObserver o) {
		observer = o;
	}

	@Override
	public void stop() {
			if (log.isDebugEnabled()) 
				log.debug("Stopping VIDEO stream for {}", freeswitchToBbbVideoStreamName);

			rtpStreamReceiver.stop();

			if (log.isDebugEnabled()) 
				log.debug("Stopped RTP VIDEO Stream Receiver for {}", freeswitchToBbbVideoStreamName);

			if (videoBroadcastStream != null) {
				videoBroadcastStream.stop();

				if (log.isDebugEnabled()) 
					log.debug("Stopped videoBroadcastStream for {}", freeswitchToBbbVideoStreamName);

				videoBroadcastStream.close();

			    if (log.isDebugEnabled()) 
			    	log.debug("Closed videoBroadcastStream for {}", freeswitchToBbbVideoStreamName);
			} 

			else
				if (log.isDebugEnabled()) 
					log.debug("videoBroadcastStream is null, couldn't stop");

		    if (log.isDebugEnabled()) 
		    	log.debug("VIDEO Stream(s) stopped");

	}	

	@Override
	public void start() {
		if (log.isDebugEnabled()) 
			log.debug("started publishing VIDEO stream in scope=[" + scope.getName() + "] path=[" + scope.getPath() + "]");

		videoBroadcastStream = new BroadcastStream(freeswitchToBbbVideoStreamName);
		videoBroadcastStream.setPublishedName(freeswitchToBbbVideoStreamName);
		videoBroadcastStream.setScope(scope);
		
		IContext context = scope.getContext();
		
		IProviderService providerService = (IProviderService) context.getBean(IProviderService.BEAN_NAME);
		if (providerService.registerBroadcastStream(scope, freeswitchToBbbVideoStreamName, videoBroadcastStream)){
			// Do nothing. Successfully registered a live broadcast stream. (ralam Sept. 4, 2012)
		} else{
			log.error("could not register broadcast stream");
			throw new RuntimeException("could not register broadcast stream");
		}
		
	    videoBroadcastStream.start();	      	
	    rtpStreamReceiver.start();
	}

	@Override
	public void onStoppedReceiving() {
		if (observer != null) observer.onStreamStopped();
	}

	@Override
	public void onPausedReceiving() {
		if(observer != null) observer.onStreamPaused();
	}

	@Override
	public void onStartedReceiving() {
		if(observer != null) observer.onStreamStarted();
	}

	@Override
	public void onMediaDataReceived(byte[] mediaData, int offset, int len) {

		for (RTMPPacketInfo packetInfo: converter.rtpToRTMP(  new RtpPacket(mediaData, (offset+len) ))) {
                pushVideo(packetInfo.data, packetInfo.ts);
        }   		

	}	

	private void pushVideo(byte[] video, long timestamp) {	

		videoBuffer.clear();
		videoBuffer.put(video);
		videoBuffer.flip();


        videoData.setTimestamp((int)(timestamp));
        videoData.setData(videoBuffer);

		videoBroadcastStream.dispatchEvent(videoData);

		videoData.release();
    }	

	@Override
	public void onFirRequest() {
		if(observer != null)
			observer.onFirRequest();
	}
}
