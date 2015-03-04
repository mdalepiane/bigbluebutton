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
package org.bigbluebutton.voiceconf.red5;

import java.text.MessageFormat;

import org.slf4j.Logger;
import org.bigbluebutton.voiceconf.sip.CallManager;
import org.bigbluebutton.voiceconf.sip.FFmpegCommand;
import org.bigbluebutton.voiceconf.sip.PeerNotFoundException;
import org.bigbluebutton.voiceconf.sip.ProcessMonitor;
import org.bigbluebutton.voiceconf.sip.SipPeerManager;
import org.bigbluebutton.voiceconf.sip.GlobalCall;
import org.red5.app.sip.codecs.Codec;
import org.red5.app.sip.codecs.H264Codec;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.stream.IBroadcastStream;
import org.bigbluebutton.voiceconf.sip.GlobalCall;
import org.bigbluebutton.voiceconf.sip.ProcessMonitor;
import org.red5.app.sip.codecs.H264Codec;
import org.red5.app.sip.codecs.Codec;

public class Service {
    private static Logger log = Red5LoggerFactory.getLogger(Service.class, "sip");

    private SipPeerManager sipPeerManager;
	
	private MessageFormat callExtensionPattern = new MessageFormat("{0}");
	private ProcessMonitor processMonitor = null;
    	
	public Boolean call(String peerId, String callerName, String destination, Boolean listenOnly) {
		if (listenOnly) {
			if (GlobalCall.reservePlaceToCreateGlobal(destination)) {
			    log.warn("Global call for {} not found, creating one", destination);
				String extension = callExtensionPattern.format(new String[] { destination });
				try {
					sipPeerManager.call(peerId, destination, "GLOBAL_AUDIO_" + destination, extension);
					Red5.getConnectionLocal().setAttribute("VOICE_CONF_PEER", peerId);
				} catch (PeerNotFoundException e) {
					log.error("PeerNotFound {}", peerId);
					return false;
				}
			}
			sipPeerManager.connectToGlobalStream(peerId, getClientId(), callerName, destination);
			Red5.getConnectionLocal().setAttribute("VOICE_CONF_PEER", peerId);
			return true;
		} else {
			Boolean result = call(peerId, callerName, destination);
			return result;
		}
	}

	public Boolean call(String peerId, String callerName, String destination) {
    	String clientId = Red5.getConnectionLocal().getClient().getId();
    	String userid = getUserId();
    	String username = getUsername();		
    log.debug("{} is requesting to join into the conference {}.", username +"[peerId="+ peerId + "][uid=" + userid + "][clientid=" + clientId + "]", destination);
		
		String extension = callExtensionPattern.format(new String[] { destination });
		try {
			sipPeerManager.call(peerId, getClientId(), callerName, extension);
			Red5.getConnectionLocal().setAttribute("VOICE_CONF_PEER", peerId);
			return true;
		} catch (PeerNotFoundException e) {
			log.error("PeerNotFound {}", peerId);
			return false;
		}
	}

	public Boolean hangup(String peerId) {
    	String clientId = Red5.getConnectionLocal().getClient().getId();
    	String userid = getUserId();
    	String username = getUsername();		
    	log.debug("{} is requesting to hang up from the conference.", username + "[uid=" + userid + "][clientid=" + clientId + "]");
		try {
			sipPeerManager.hangup(peerId, getClientId());
			return true;
		} catch (PeerNotFoundException e) {
			log.error("PeerNotFound {}", peerId);
			return false;
		}
	}
	
	public Boolean webRTCVideoSend(String videoParameters) {
		String[] parameters = videoParameters.split(",");
		Codec codec;
		FFmpegCommand ffmpeg;
		
		log.debug("webRTC Video Parameters:" + videoParameters);
		
    	String clientId = Red5.getConnectionLocal().getClient().getId();
    	String userid = getUserId();
    	String username = getUsername();
    	
    	String ip = Red5.getConnectionLocal().getHost();
        String remoteVideoPort = parameters[2].split("=")[1];
        String localVideoPort = parameters[3].split("=")[1];
    	String streamPath = parameters[4].replace("]", "").toLowerCase();

        codec = new H264Codec();
    	
    	log.debug("{} is requesting to send video through webRTC. " + "[uid=" + userid + "][clientid=" + clientId + "]", username);    	
        log.debug("Video Parameters: remotePort = "+remoteVideoPort+ ", localPort = "+localVideoPort+" rtmp-stream = "+streamPath);
    	String inputLive = streamPath+" live=1";
		String output = "rtp://" + ip + ":" + remoteVideoPort + "?localport=" + localVideoPort;
		
		ffmpeg = new FFmpegCommand();
		ffmpeg.setFFmpegPath("/usr/local/bin/ffmpeg");
		ffmpeg.setInput(inputLive);
		ffmpeg.setCodec("h264");
		ffmpeg.setPreset("ultrafast");
		ffmpeg.setProfile("baseline");
		ffmpeg.setLevel("1.3");
		ffmpeg.setFormat("rtp");
		ffmpeg.setPayloadType(String.valueOf(codec.getCodecId()));
		ffmpeg.setLoglevel("quiet");
		ffmpeg.setSliceMaxSize("1024");
		ffmpeg.setMaxKeyFrameInterval("10");
		ffmpeg.setOutput(output);
		
		//String[] command = ffmpeg.getFFmpegCommand(true);
		String[] command = {"/usr/local/bin/ffmpeg", "-i", inputLive, "-vcodec", "vp8",
				"-f", "rtp", "-payload_type", "105", output};
		
		// Free local port before starting ffmpeg
		//localVideoSocket.close();
		
		log.debug("Preparing FFmpeg process monitor");
		
		processMonitor = new ProcessMonitor(command);
		processMonitor.start();
	   	return true;
	}
	
	public Boolean acceptWebRTCCall(String portParameters){
        //called by the client
        String[] parameters = portParameters.split(",");
        log.debug("Accepted a webRTC Call: saving it's parameters"+portParameters);
        String clientId = Red5.getConnectionLocal().getClient().getId();
        String userid = getUserId();
        String username = getUsername();
        String ip = Red5.getConnectionLocal().getHost();

        String remoteVideoPort = parameters[2].split("=")[1];
        String localVideoPort = parameters[3].split("=")[1].replace("]",""); 	
        String peerId = "default";
        //String videoStream = sipPeerManager.CallManager.getVideoStream(userid).getPublishedName();
        try{
            if (sipPeerManager != null) sipPeerManager.startBbbToFreeswitchWebRTCVideoStream(peerId, userid, ip,remoteVideoPort, localVideoPort);
            else log.debug("There's no SipPeerManager to handle this webRTC Video Call. Aborting... ");
        } catch (PeerNotFoundException e) {
            log.error("PeerNotFound {}", peerId);
            return false;
        }
        return true;
	}

	public Boolean hangUpWebRTCCAll(String peerId){
        String userid = getUserId();
        log.debug("hanging up webRTC Call on voice's context");
        try{
            sipPeerManager.stopBbbToFreeswitchWebRTCVideoStream(peerId, userid);
        } catch (PeerNotFoundException e) {
            log.error("PeerNotFound {}", peerId);
            return false;
        }
        return true;
	}

	private String getClientId() {
		IConnection conn = Red5.getConnectionLocal();
		return conn.getClient().getId();
	}
	
	public void setCallExtensionPattern(String callExtensionPattern) {
		this.callExtensionPattern = new MessageFormat(callExtensionPattern);
	}
	
	public void setSipPeerManager(SipPeerManager sum) {
		sipPeerManager = sum;
	}
	
	private String getUserId() {
		String userid = (String) Red5.getConnectionLocal().getAttribute("USERID");
		if ((userid == null) || ("".equals(userid))) userid = "unknown-userid";
		return userid;
	}
	
	private String getUsername() {
		String username = (String) Red5.getConnectionLocal().getAttribute("USERNAME");
		if ((username == null) || ("".equals(username))) username = "UNKNOWN-CALLER";
		return username;
	}
}
