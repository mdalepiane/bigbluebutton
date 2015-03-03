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

package org.bigbluebutton.modules.phone.managers {	
	import com.asfusion.mate.events.Dispatcher;
	
	import flash.events.AsyncErrorEvent;
	import flash.events.Event;
	import flash.events.IEventDispatcher;
	import flash.events.NetStatusEvent;
	import flash.events.SecurityErrorEvent;
	import flash.external.*;
	import flash.net.NetConnection;
	import flash.net.NetStream;
	
	import org.bigbluebutton.common.LogUtil;
	import org.bigbluebutton.core.BBB;
	import org.bigbluebutton.core.UsersUtil;
	import org.bigbluebutton.core.model.MeetingModel;
	import org.bigbluebutton.main.api.JSLog;
	import org.bigbluebutton.main.events.BBBEvent;
	import org.bigbluebutton.modules.phone.events.FlashCallConnectedEvent;
	import org.bigbluebutton.modules.phone.events.FlashCallDisconnectedEvent;
	import org.bigbluebutton.modules.phone.events.FlashVoiceConnectionStatusEvent;
	import org.bigbluebutton.modules.phone.events.RegistrationFailedEvent;
	import org.bigbluebutton.modules.phone.events.RegistrationSuccessEvent;
	
	public class ConnectionManager {
    private static const LOG:String = "Phone::ConnectionManager - ";
    
		private  var netConnection:NetConnection = null;
		private var incomingNetStream:NetStream = null;
		private var outgoingNetStream:NetStream = null;
		private var username:String;
		private var uri:String;
    private var externUserId:String;
		private var uid:String;
		private var meetingId:String;
		private var voiceBridge:String;
		
		private var registered:Boolean = false;
		private var closedByUser:Boolean = false;

		private var dispatcher:Dispatcher;
		
		public function ConnectionManager():void {
			dispatcher = new Dispatcher();
		}
		
    public function isConnected():Boolean {
      if (netConnection != null) {
        return netConnection.connected;
      }
      return false;
    }
    
		public function getConnection():NetConnection {
			return netConnection;
		}
		
    public function setup(uid:String, externUserId:String, username:String, meetingId:String, uri:String, voiceBridge:String):void {
      trace(LOG + "Setup uid=[" + uid + "] extuid=[" + externUserId + "] name=[" + username + "] uri=[" + uri + "] voiceBridge=[" + voiceBridge + "]");
      this.uid = uid;	
      this.username  = username;
      this.meetingId = meetingId;
      this.uri   = uri;
      this.externUserId = externUserId;
      this.voiceBridge = voiceBridge;
    }

		public function connect():void {				
      closedByUser = false;
      var isTunnelling:Boolean = BBB.initConnectionManager().isTunnelling;
      if (isTunnelling) {
        uri = uri.replace(/rtmp:/gi, "rtmpt:");
      }
      trace(LOG + "Connecting to uri=[" + uri + "]");
			NetConnection.defaultObjectEncoding = flash.net.ObjectEncoding.AMF0;	
			netConnection = new NetConnection();
			netConnection.proxyType = "best";
			netConnection.client = this;
			netConnection.addEventListener( NetStatusEvent.NET_STATUS , netStatus );
			netConnection.addEventListener(SecurityErrorEvent.SECURITY_ERROR, securityErrorHandler);
			netConnection.connect(uri, meetingId, externUserId, username, voiceBridge);
		}

		public function disconnect(requestByUser:Boolean):void {
      closedByUser = requestByUser;
      if (netConnection != null) {
        netConnection.close();
      }			
		}
		
    private function handleConnectionClosed():void {
      if (!closedByUser) {
        dispatcher.dispatchEvent(new FlashVoiceConnectionStatusEvent(FlashVoiceConnectionStatusEvent.DISCONNECTED));
      }
    }
		private function netStatus (event:NetStatusEvent ):void {		 
      var info : Object = event.info;
      var statusCode : String = info.code;
      
      var logData:Object = new Object();       
      logData.user = UsersUtil.getUserData();
      
      switch (statusCode) {
        case "NetConnection.Connect.Success":
          trace(LOG + "Connection success");
          JSLog.debug("Successfully connected to BBB Voice", logData);
          dispatcher.dispatchEvent(new FlashVoiceConnectionStatusEvent(FlashVoiceConnectionStatusEvent.CONNECTED));           
          // Force freeswitch video start
          if(MeetingModel.getInstance().freeswitchVideoName != "")
            videoStarted(MeetingModel.getInstance().freeswitchVideoName);
          break;
        case "NetConnection.Connect.Failed":
          trace(LOG + "Connection failed");
          JSLog.error("Failed to connect to BBB Voice", logData);
          dispatcher.dispatchEvent(new FlashVoiceConnectionStatusEvent(FlashVoiceConnectionStatusEvent.FAILED));
          break;
        case "NetConnection.Connect.NetworkChange":
          trace(LOG + "Detected network change. User might be on a wireless and temporarily dropped connection. Doing nothing. Just making a note.");
          JSLog.warn("Detected network change to BBB Voice", logData);
          break;
        case "NetConnection.Connect.Closed":
          trace(LOG + "Connection closed");
          JSLog.debug("Disconnected from BBB Voice", logData);
          handleConnectionClosed();
          break;
      }
		} 
		
		private function asyncErrorHandler(event:AsyncErrorEvent):void {
      trace(LOG + "AsyncErrorEvent: " + event);
    }
		
		private function securityErrorHandler(event:SecurityErrorEvent):void {
      trace(LOG + "securityErrorHandler: " + event);
    }
        
    //********************************************************************************************
		//			
		//			CallBack Methods from Red5 
		//
		//********************************************************************************************
		public function failedToJoinConferenceCallback(msg:String):* {
			trace(LOG + "failedToJoinConferenceCallback " + msg);
			var event:FlashCallDisconnectedEvent = new FlashCallDisconnectedEvent();
			dispatcher.dispatchEvent(event);	
		}
		
		public function disconnectedFromJoinConferenceCallback(msg:String):* {
			trace(LOG + "disconnectedFromJoinConferenceCallback " + msg);
			var event:FlashCallDisconnectedEvent = new FlashCallDisconnectedEvent();
			dispatcher.dispatchEvent(event);	
		}	
				
		public function successfullyJoinedVoiceConferenceCallback(publishAudioName:String, playAudioName:String, audioCodec:String):* {
			trace(LOG + "successfullyJoinedConferenceCallback [" + publishAudioName + "] : [" + playAudioName + "] : [" + audioCodec + "]");

			var audioEvent:FlashCallConnectedEvent = new FlashCallConnectedEvent(publishAudioName, playAudioName, audioCodec);
			dispatcher.dispatchEvent(audioEvent);
		}

		public function videoPaused():void {
			LogUtil.debug("video is paused. Closing video window");
			MeetingModel.getInstance().freeswitchVideoName = "";
			var videoPaused:BBBEvent = new BBBEvent(BBBEvent.FREESWITCH_VIDEO_PAUSED);
			dispatcher.dispatchEvent(videoPaused);
		}

		public function videoStarted(videoStream:String):void {
			LogUtil.debug("video is starting. Opening video window");
			MeetingModel.getInstance().freeswitchVideoName = videoStream;
			var videoStarted:BBBEvent = new BBBEvent(BBBEvent.FREESWITCH_VIDEO_STARTED);
			videoStarted.payload.streamName = videoStream;
			videoStarted.payload.connection = netConnection;
			dispatcher.dispatchEvent(videoStarted);
		}

								
		//********************************************************************************************
		//			
		//			SIP Actions
		//
		//********************************************************************************************		
		public function doCall(dialStr:String, listenOnly:Boolean = false):void {
			trace(LOG + "in doCall - Calling " + dialStr + (listenOnly? " *listen only*": ""));
			netConnection.call("voiceconf.call", null, "default", username, dialStr, listenOnly.toString());
		}
				
		public function doHangUp():void {			
			if (isConnected()) {
        trace(LOG + "hanging up call");
				netConnection.call("voiceconf.hangup", null, "default");
			}
		}

		public function webRTCVideoSend(videoParameters:String):void{
      		//for test only 
			netConnection.call("voiceconf.webRTCVideoSend", null, "default", username, videoParameters);
			trace(LOG + "webRTC videoData sent to bbb-voice application. Video parameters: " + videoParameters);
		}

		public function onWebRTCCallAccepted(portParameters:String):void{
			trace(LOG + "webRTC Call Accepted: communicating with bbb-voice");
			netConnection.call("voiceconf.acceptWebRTCCall", null, "default", username, portParameters);
		}

		public function doHangUpWebRTCCall():void{
			if (isConnected()) {
		        trace(LOG + "hanging up webRTC Call on bbb-voice's context");
						netConnection.call("voiceconf.hangUpWebRTCCall", null, "default");
			}
		}
		
		public function onBWCheck(... rest):Number { 
			return 0; 
		} 
		public function onBWDone(... rest):void { 
			var p_bw:Number; 
			if (rest.length > 0) p_bw = rest[0]; 
			// your application should do something here 
			// when the bandwidth check is complete 
			trace("bandwidth = " + p_bw + " Kbps."); 
		}
	}
}
