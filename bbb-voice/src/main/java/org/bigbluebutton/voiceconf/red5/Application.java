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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.bigbluebutton.voiceconf.sip.PeerNotFoundException;
import org.bigbluebutton.voiceconf.sip.SipPeerManager;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.service.IServiceCapableConnection;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.stream.ClientBroadcastStream;
import com.google.gson.Gson;
import org.bigbluebutton.voiceconf.sip.GlobalCall;
import java.text.MessageFormat;

public class Application extends MultiThreadedApplicationAdapter {
	private static Logger log = Red5LoggerFactory.getLogger(Application.class, "sip");

  private SipPeerManager sipPeerManager;
  private ClientConnectionManager clientConnManager;
    
  private String sipServerHost = "localhost";
  private String sipClientRtpIp = "";
  private int sipPort = 5070;
  private int startAudioPort = 3000;
	private int stopAudioPort = 3029;
    private int startVideoPort = 3030;
    private int stopVideoPort = 3059;

	private String password = "secret";
	private String username;
	private CallStreamFactory callStreamFactory;
    private MessageFormat callExtensionPattern = new MessageFormat("{0}");
	
    @Override
    public boolean appStart(IScope scope) {
    	log.debug("VoiceConferenceApplication appStart[" + scope.getName() + "]");
    	super.setScope(scope);
    	callStreamFactory = new CallStreamFactory();
    	callStreamFactory.setScope(scope);
    	sipPeerManager.setCallStreamFactory(callStreamFactory);
      sipPeerManager.setClientConnectionManager(clientConnManager);
      sipPeerManager.createSipPeer("default", sipClientRtpIp, sipServerHost, sipPort, startAudioPort, stopAudioPort, startVideoPort, stopVideoPort);
      try {
      	sipPeerManager.register("default", username, password);
      } catch (PeerNotFoundException e) {
      	// TODO Auto-generated catch block
      	e.printStackTrace();
      }
      return super.appStart(scope);
    }

    @Override
    public void appStop(IScope scope) {
        log.debug("VoiceConferenceApplication appStop[" + scope.getName() + "]");
        sipPeerManager.destroyAllSessions();
        super.appStop(scope);
    }

    @Override
    public boolean appConnect(IConnection conn, Object[] params) {
      if (params.length == 0) {
          params = new Object[4];
          params[0] = "unknown-meetingid";
          params[1] = "unknown-userid";
          params[2] = "UNKNOWN-CALLER";
          params[3] = "UNKNOWN-VOICEBRIDGE";
      }
    	String meetingId = ((String) params[0]).toString();
    	String userId = ((String) params[1]).toString();
      String username = ((String) params[2]).toString();
      String voiceBridge = ((String) params[3]).toString();
      String clientId = Red5.getConnectionLocal().getClient().getId();
      String remoteHost = Red5.getConnectionLocal().getRemoteAddress();
      int remotePort = Red5.getConnectionLocal().getRemotePort();
        
      if ((meetingId == null) || ("".equals(meetingId))) userId = "unknown-meetingid";
      if ((userId == null) || ("".equals(userId))) userId = "unknown-userid";
      if ((username == null) || ("".equals(username))) username = "UNKNOWN-CALLER";
      if ((voiceBridge == null) || ("".equals(voiceBridge))) voiceBridge = "UNKNOWN-VOICEBRIDGE";
      Red5.getConnectionLocal().setAttribute("MEETING_ID", meetingId);
      Red5.getConnectionLocal().setAttribute("USERID", userId);
      Red5.getConnectionLocal().setAttribute("USERNAME", username);
      Red5.getConnectionLocal().setAttribute("VOICEBRIDGE", voiceBridge);
        
      log.info("{} [clientid={}] has connected to the voice conf app.", username + "[uid=" + userId + "] [voiceBridge="+voiceBridge+"]", clientId);
      log.info("[clientid={}] connected from {}.", clientId, remoteHost + ":" + remotePort);
      
  		String connType = getConnectionType(Red5.getConnectionLocal().getType());
  		String userFullname = username;
  		String connId = Red5.getConnectionLocal().getSessionId();
  		
  		Map<String, Object> logData = new HashMap<String, Object>();
  		logData.put("meetingId", meetingId);
  		logData.put("connType", connType);
  		logData.put("connId", connId);
  		logData.put("userId", userId);
  		logData.put("username", userFullname);
  		logData.put("event", "user_joining_bbb_voice");
  		logData.put("description", "User joining BBB Voice.");
  		
  		Gson gson = new Gson();
      String logStr =  gson.toJson(logData);
  		
  		log.info("User joining bbb-voice: data={}", logStr);
      
        if(!voiceBridge.equals("UNKNOWN-VOICEBRIDGE")) {
            clientConnManager.createClient(clientId, userId, username, (IServiceCapableConnection) Red5.getConnectionLocal());

            String peerId = "default";
            createGlobalAudio(clientId,peerId,username,voiceBridge);
            GlobalCall.addUser(clientId, username, voiceBridge);
            Red5.getConnectionLocal().setAttribute("VOICE_CONF_PEER", peerId);
            if(!GlobalCall.isVideoPaused(voiceBridge)) {
                // There is already a video stream running, must inform new client
                String videoStreamName = GlobalCall.getGlobalVideoStream(voiceBridge);
                log.debug("Informing new user [{}] about current global video stream [{}]", clientId, videoStreamName);
                clientConnManager.startedVideo(clientId, videoStreamName);
            }
        }
        else {
            log.warn("Voice bridge not informed during appConnect. Global will not be created.");
        }
      return super.appConnect(conn, params);
    }
    
  	private String getConnectionType(String connType) {
  		if ("persistent".equals(connType.toLowerCase())) {
  			return "RTMP";
  		} else if("polling".equals(connType.toLowerCase())) {
  			return "RTMPT";
  		} else {
  			return connType.toUpperCase();
  		}
  	}

    @Override
    public void appDisconnect(IConnection conn) {
    	String clientId = Red5.getConnectionLocal().getClient().getId();
    	String userId = getUserId();
    	String username = getUsername();
    	String voiceBridge = (String) Red5.getConnectionLocal().getAttribute("VOICEBRIDGE");
    	
      String remoteHost = Red5.getConnectionLocal().getRemoteAddress();
      int remotePort = Red5.getConnectionLocal().getRemotePort();    	
    	log.info("[clientid={}] disconnnected from {}.", clientId, remoteHost + ":" + remotePort);
      log.debug("{} [clientid={}] is leaving the voice conf app. Removing from ConnectionManager.", username + "[uid=" + userId + "]", clientId);
      log.debug("[appDisconnect] Voice Bridge: " + voiceBridge);
    	
  		String connType = getConnectionType(Red5.getConnectionLocal().getType());
  		String userFullname = username;
  		String connId = Red5.getConnectionLocal().getSessionId();
  		
  		Map<String, Object> logData = new HashMap<String, Object>();
  		logData.put("meetingId", getMeetingId());
  		logData.put("connType", connType);
  		logData.put("connId", connId);
  		logData.put("userId", userId);
  		logData.put("username", userFullname);
  		logData.put("event", "user_leaving_bbb_voice");
  		logData.put("description", "User leaving BBB Voice.");
  		
  		Gson gson = new Gson();
      String logStr =  gson.toJson(logData);
  		
  		log.info("User leaving bbb-voice: data={}", logStr);
      
      clientConnManager.removeClient(clientId);

        String peerId = (String) Red5.getConnectionLocal().getAttribute("VOICE_CONF_PEER");

        if(voiceBridge != "UNKNOWN-VOICEBRIDGE") {
            GlobalCall.removeUser(clientId, voiceBridge);

            boolean roomRemoved = GlobalCall.removeRoomIfUnused(voiceBridge);
            log.debug("Should the global connection be removed? {}", roomRemoved? "yes": "no");
            if (roomRemoved) {
                try {
                    log.debug("Hanging up the global audio call {}", voiceBridge);
                    sipPeerManager.hangup(peerId, "GLOBAL_AUDIO_" + voiceBridge);
                } catch (PeerNotFoundException e) {
                    log.warn("Peer {} not found. Unable to hangup the global call.", "GLOBAL_AUDIO_" + voiceBridge);
                }
            }
        }

      super.appDisconnect(conn);
    }
    
    @Override
    public void streamPublishStart(IBroadcastStream stream) {
        String clientId = Red5.getConnectionLocal().getClient().getId();
        String userId = getUserId();
        String username = getUsername();
        String videoUserId;
        log.debug("{} has started publishing stream [{}]", username + "[uid=" + userId + "][clientid=" + clientId + "]", stream.getPublishedName());
        //System.out.println("streamPublishStart: " + stream.getPublishedName());
        IConnection conn = Red5.getConnectionLocal();
        String peerId = (String) conn.getAttribute("VOICE_CONF_PEER");
        
        if(isVideoStream(stream)){
            super.streamPublishStart(stream);
            videoUserId = getBbbVideoUserId(stream);
            log.debug("Video UserId: " + videoUserId);
            peerId = "default";
            sipPeerManager.startBbbToFreeswitchVideoStream(peerId, videoUserId, stream, conn.getScope());

        } else if (peerId != null) {
            super.streamPublishStart(stream);
            videoUserId = userId;
            log.debug("Starting Audio Stream for the user ["+videoUserId+"]");
            sipPeerManager.startBbbToFreeswitchAudioStream(peerId, videoUserId, clientId, stream, conn.getScope());
//            recordStream(stream);
        }
    }

    private boolean createGlobalAudio(String clientId,String peerId, String callerName, String destination){
        log.debug("peerId = "+peerId+" callerName = "+ callerName + " destination = "+ destination);

        if (GlobalCall.reservePlaceToCreateGlobal(destination)) {
                String extension = callExtensionPattern.format(new String[] { destination });
                try {
                    sipPeerManager.call(peerId, clientId, "GLOBAL_AUDIO_" + destination, extension);
                    Red5.getConnectionLocal().setAttribute("VOICE_CONF_PEER", peerId);
                } catch (PeerNotFoundException e) {
                    log.error("PeerNotFound {}", peerId);
                    return false;
                }
        }
        return true;
    }

    /**
     * A hook to record a sample stream. A file is written in webapps/sip/streams/
     * @param stream
     */
    private void recordStream(IBroadcastStream stream) {
    	IConnection conn = Red5.getConnectionLocal();     
    	String streamName = stream.getPublishedName();
     
    	try {
    		ClientBroadcastStream cstream = (ClientBroadcastStream) this.getBroadcastStream(conn.getScope(), stream.getPublishedName() );
    		cstream.saveAs(streamName, false);
    	} catch(Exception e) {
    		System.out.println("ERROR while recording stream " + e.getMessage());
    		e.printStackTrace();
    	}    	
    }
    
    @Override
    public void streamBroadcastClose(IBroadcastStream stream) {
    	String clientId = Red5.getConnectionLocal().getClient().getId();
    	String userId = getUserId();
    	String username = getUsername();
    	
    	log.debug("{} has stopped publishing stream [{}]", username + "[uid=" + userId + "][clientid=" + clientId + "]", stream.getPublishedName());
    	IConnection conn = Red5.getConnectionLocal();
    	String peerId = (String) conn.getAttribute("VOICE_CONF_PEER");

        if(isVideoStream(stream)){
            userId = getBbbVideoUserId(stream);
            log.debug("Closing only the video stream of the UserId " + userId);
            peerId = "default";
            sipPeerManager.stopBbbToFreeswitchVideoStream(peerId, userId, stream, conn.getScope());
            super.streamBroadcastClose(stream);
        } 

        else if (peerId != null) {  
            log.debug("Audio disconnected: closing AUDIO and VIDEO streams");	
	    	sipPeerManager.stopBbbToFreeswitchAudioStream(peerId, clientId, stream, conn.getScope());
            sipPeerManager.stopBbbToFreeswitchVideoStream(peerId, userId, stream, conn.getScope());
	    	super.streamBroadcastClose(stream);    
        }
    }
    
    public Set<String> getStreams() {
        IConnection conn = Red5.getConnectionLocal();
        return getBroadcastStreamNames( conn.getScope() );
    }

    public void onPing() {
    	log.debug( "Red5SIP Ping" );
    }
		
    public void setSipServerHost(String h) {
    	sipServerHost = h.trim();
    }
    
    public void setSipClientRtpIp(String ipAddr) {
    	this.sipClientRtpIp = ipAddr.trim();
    }
    
    public void setUsername(String un) {
    	this.username = un.trim();
    }
    
    public void setPassword(String pw) {
    	this.password = pw.trim();
    }
    
    public void setSipPort(int sipPort) {
		this.sipPort = sipPort;
	}

	public void setStartAudioPort(int startRTPPort) {
		this.startAudioPort = startRTPPort;
	}

	public void setStopAudioPort(int stopRTPPort) {
		this.stopAudioPort = stopRTPPort;
	}

    public void setStartVideoPort(int startRTPPort) {
        this.startVideoPort = startRTPPort;
    }

    public void setStopVideoPort(int stopRTPPort) {
        this.stopVideoPort = stopRTPPort;
    }    
	
	public void setSipPeerManager(SipPeerManager spm) {
		sipPeerManager = spm;
	}
	
	public void setClientConnectionManager(ClientConnectionManager ccm) {
		clientConnManager = ccm;
	}
	
	private String getUserId() {
		String userid = (String) Red5.getConnectionLocal().getAttribute("USERID");
		if ((userid == null) || ("".equals(userid))) userid = "unknown-userid";
		return userid;
	}
	
	private String getMeetingId() {
		String meetingId = (String) Red5.getConnectionLocal().getAttribute("MEETING_ID");
		if ((meetingId == null) || ("".equals(meetingId))) meetingId = "unknown-meetingid";
		return meetingId;
	}

    private boolean isVideoStream(IBroadcastStream stream){
        return stream.getPublishedName().matches("\\d+x\\d+-\\w+-\\d+"); //format: <width>x<height>-<userid>-<timestamp>
    }

    private String getBbbVideoUserId(IBroadcastStream videoStream) {
        String publishedName = videoStream.getPublishedName();
        String userId = "";        
        userId = publishedName.split("-")[1];
        
        return userId;
    }
    
	private String getUsername() {
		String username = (String) Red5.getConnectionLocal().getAttribute("USERNAME");
		if ((username == null) || ("".equals(username))) username = "UNKNOWN-CALLER";
		return username;
	}    
}
