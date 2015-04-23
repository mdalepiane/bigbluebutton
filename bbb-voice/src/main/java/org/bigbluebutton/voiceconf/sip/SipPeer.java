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
package org.bigbluebutton.voiceconf.sip;

import java.util.Collection;
import java.util.Iterator;

import org.zoolu.sip.provider.*;
import org.zoolu.net.SocketAddress;
import org.slf4j.Logger;
import org.bigbluebutton.voiceconf.messaging.IMessagingService;
import org.bigbluebutton.voiceconf.red5.CallStreamFactory;
import org.bigbluebutton.voiceconf.red5.ClientConnectionManager;
import org.red5.app.sip.codecs.Codec;
import org.red5.app.sip.codecs.H264Codec;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;

/**
 * Class that is a peer to the sip server. This class will maintain
 * all calls to it's peer server.
 * @author Richard Alam
 *
 */
public class SipPeer implements SipRegisterAgentListener {
    private static Logger log = Red5LoggerFactory.getLogger(SipPeer.class, "sip");

    private ClientConnectionManager clientConnManager;
    private CallStreamFactory callStreamFactory;
    
    private CallManager callManager = new CallManager();
    private IMessagingService messagingService;
    private SipProvider sipProvider;
    private String clientRtpIp;
    private SipRegisterAgent registerAgent;
    private final String id;
    private final ConferenceProvider confProvider;
    
    private boolean registered = false;
    private SipPeerProfile registeredProfile;
    private ProcessMonitor processMonitor = null;
    
    public SipPeer(String id, String sipClientRtpIp, String host, int sipPort, 
			int startAudioPort, int stopAudioPort, int startVideoPort, int stopVideoPort, IMessagingService messagingService) {
        this.id = id;
        this.clientRtpIp = sipClientRtpIp;
        this.messagingService = messagingService;
        confProvider = new ConferenceProvider(host, sipPort, startAudioPort, stopAudioPort, startVideoPort, stopVideoPort);
        initSipProvider(host, sipPort);
    }
    
    private void initSipProvider(String host, int sipPort) {
        sipProvider = new SipProvider(host, sipPort);    
        sipProvider.setOutboundProxy(new SocketAddress(host)); 
        sipProvider.addSipProviderListener(new OptionMethodListener());    	
    }
    
    public void register(String username, String password) {
    	log.debug( "SIPUser register" );
        createRegisterUserProfile(username, password);
        if (sipProvider != null) {
        	registerAgent = new SipRegisterAgent(sipProvider, registeredProfile.fromUrl, 
        			registeredProfile.contactUrl, registeredProfile.username, 
        			registeredProfile.realm, registeredProfile.passwd);
        	registerAgent.addListener(this);
        	registerAgent.register(registeredProfile.expires, registeredProfile.expires/2, registeredProfile.keepaliveTime);
        }                              
    }
    
    private void createRegisterUserProfile(String username, String password) {    	    	
    	registeredProfile = new SipPeerProfile();
    	registeredProfile.audioPort = confProvider.getStartAudioPort();
            	
        String fromURL = "\"" + username + "\" <sip:" + username + "@" + confProvider.getHost() + ">";
        registeredProfile.username = username;
        registeredProfile.passwd = password;
        registeredProfile.realm = confProvider.getHost();
        registeredProfile.fromUrl = fromURL;
        registeredProfile.contactUrl = "sip:" + username + "@" + sipProvider.getViaAddress();
        if (sipProvider.getPort() != SipStack.default_port) {
        	registeredProfile.contactUrl += ":" + sipProvider.getPort();
        }		
        registeredProfile.keepaliveTime=8000;
        registeredProfile.acceptTime=0;
        registeredProfile.hangupTime=20;   
        
        log.debug( "SIPUser register : {}", fromURL );
        log.debug( "SIPUser register : {}", registeredProfile.contactUrl );
    }

    public void call(String clientId, String callerName, String destination) {
    	if (!registered) {
    		/* 
    		 * If we failed to register with FreeSWITCH, reject all calls right away.
    		 * This way the user will know that there is a problem as quickly as possible.
    		 * If we pass the call, it take more that 30seconds for the call to timeout
    		 * (in case FS is offline) and the user will be kept wondering why the call
    		 * isn't going through.
    		 */
    		log.warn("We are not registered to FreeSWITCH. However, we will allow {} to call {}.", callerName, destination);
//    		return;
    	}

    	CallAgent ca = createCallAgent(clientId);

    	ca.call(callerName, destination);
    	callManager.add(ca);
    }

	public void connectToGlobalStream(String clientId, String callerIdName, String destination) {
    	CallAgent ca = createCallAgent(clientId);
	    
    	ca.connectToGlobalStream(clientId, callerIdName, destination); 	
     	callManager.add(ca);
	}

    private CallAgent createCallAgent(String clientId) {
    	SipPeerProfile callerProfile = SipPeerProfile.copy(registeredProfile);
    	CallAgent ca = new CallAgent(this.clientRtpIp, sipProvider, callerProfile, confProvider, clientId, messagingService);
    	ca.setClientConnectionManager(clientConnManager);
    	ca.setCallStreamFactory(callStreamFactory);

    	return ca;
    }

	public void close() {
		log.debug("SIPUser close1");
        try {
			unregister();
		} catch(Exception e) {
			log.error("close: Exception:>\n" + e);
		}

       log.debug("Stopping SipProvider");
       sipProvider.halt();
	}

    public void hangup(String clientId) {
        log.debug( "SIPUser hangup" );

        CallAgent ca = callManager.remove(clientId);

        if (ca != null) {
            if (ca.isListeningToGlobal()) {
                String destination = ca.getDestination();
                String userId = ca.getUserId();

                log.info("User has disconnected from global audio, user [{}] voiceConf {}", userId, destination);
                messagingService.userDisconnectedFromGlobalAudio(destination, userId);
            }
            ca.hangup();
        }
    }

    public void unregister() {
    	log.debug( "SIPUser unregister" );

    	Collection<CallAgent> calls = callManager.getAll();
    	for (Iterator<CallAgent> iter = calls.iterator(); iter.hasNext();) {
    		CallAgent ca = (CallAgent) iter.next();
    		ca.hangup();
    	}

        if (registerAgent != null) {
            registerAgent.unregister();
            registerAgent = null;
        }
    }

    public void startBbbToFreeswitchAudioStream(String clientId, String userId, IBroadcastStream broadcastStream, IScope scope) {
    	CallAgent ca = callManager.get(clientId);
        IBroadcastStream videoStream = callManager.getVideoStream(userId);
        IScope videoScope = callManager.getVideoScope(userId);
        log.debug("Starting Audio Stream for the user ["+userId+"]");
        if (ca != null) {
            ca.startBbbToFreeswitchAudioStream(broadcastStream, scope);
            if ((videoStream != null) && (videoScope != null)){
                log.debug(" There's a VideoStream for this audio call, starting it ");
                ca.startBbbToFreeswitchVideoStream(videoStream,videoScope);
            }
        }
    }
    
    public void stopBbbToFreeswitchAudioStream(String clientId, IBroadcastStream broadcastStream, IScope scope) {
    	CallAgent ca = callManager.get(clientId);

        if (ca != null) {
           ca.stopBbbToFreeswitchAudioStream(broadcastStream, scope);
       
        } else {
        	log.info("Can't stop talk stream as stream may have already been stopped.");
        }
    }

    public void startBbbToFreeswitchVideoStream(String userId, IBroadcastStream broadcastStream, IScope scope) {
        CallAgent ca = callManager.getByUserId(userId);
        if (ca != null) 
           ca.startBbbToFreeswitchVideoStream(broadcastStream, scope);
        else{
            log.debug("Could not START BbbToFreeswitchVideoStream: there is no CallAgent with"
                       + " userId " + userId + " (maybe this is an webRTC call?). Saving the current stream and scope to be used when the CallAgent is created by this user");
            callManager.addVideoStream(userId,broadcastStream);
            callManager.addVideoScope(userId,scope);
        }
    }
    
    public void stopBbbToFreeswitchVideoStream(String userId, IBroadcastStream broadcastStream, IScope scope) {
        CallAgent ca = callManager.getByUserId(userId);
        if (ca != null) {
           ca.stopBbbToFreeswitchVideoStream(broadcastStream, scope);
           callManager.removeVideoStream(userId);
           callManager.removeVideoScope(userId);
        }
        else
            log.debug("Could not STOP BbbToFreeswitchVideoStream: there is no CallAgent with"
                       + "userId " + userId);
        
    }

    public void startBbbToFreeswitchWebRTCVideoStream(String userId, String ip, String remoteVideoPort , String localVideoPort) {
        IBroadcastStream videoStream = callManager.getVideoStream(userId);
        IScope scope = callManager.getVideoScope(userId);
        Codec codec;
        FFmpegCommand ffmpeg;

        if (videoStream == null){
            //
            log.debug("There's no videoStream for this webRTCCall. Waiting for the user to enable your webcam");
        } else {
            //start webRTCVideoStream
            codec = new H264Codec();

            log.debug("{} is requesting to send video through webRTC. " + "[uid=" + userId + "]");    	
            log.debug("Video Parameters: remotePort = "+remoteVideoPort+ ", localPort = "+localVideoPort+" rtmp-stream = rtmp://" + ip + "/video/" + scope.getName() + "/"
                    + videoStream.getPublishedName());

            String inputLive = "rtmp://" + ip + "/video/" + scope.getName() + "/"
                    + videoStream.getPublishedName() + " live=1";
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

            String[] command = ffmpeg.getFFmpegCommand(true);

            // Free local port before starting ffmpeg
            //localVideoSocket.close();

            log.debug("Preparing FFmpeg process monitor");

            processMonitor = new ProcessMonitor(command);
            processMonitor.start();
        }

    }

    public void stopBbbToFreeswitchWebRTCVideoStream(String userId) {
        if (processMonitor != null) processMonitor.destroy();
    }

    public boolean startFreeswitchToBbbVideoStream(String userId){
        CallAgent ca = callManager.get(userId);
        if(ca != null) {
            return ca.startFreeswitchToBbbVideoStream();
        } else {
            log.debug("callAgent for {} is null", userId);
            for(CallAgent c : callManager.getAll()) {
                log.debug("{}", c.getUserId());
            }
        }
        return false;
    }
    
    public String getStreamType(String clientId, String streamName) {
        CallAgent ca = callManager.get(clientId);
        if (ca != null) {
           return ca.getStreamType(streamName);
        }
        else
        {
            log.debug("[SipPeer] Invalid clientId");
            return null;
        }
    }

    public boolean isAudioStream(String clientId, IBroadcastStream broadcastStream) {
        CallAgent ca = callManager.get(clientId);
        if (ca != null) {
           return ca.isAudioStream(broadcastStream);
        }
        else
            return false;
    }

    public boolean isVideoStream(String clientId, IBroadcastStream broadcastStream) {
        CallAgent ca = callManager.get(clientId);
        if (ca != null) {
           return ca.isVideoStream(broadcastStream);
        }
        else
            return false;
    }

	@Override
	public void onRegistrationFailure(String result) {
		log.error("Failed to register with Sip Server.");
		registered = false;
	}

	@Override
	public void onRegistrationSuccess(String result) {
		log.info("Successfully registered with Sip Server.");
		registered = true;
	}

	@Override
	public void onUnregistedSuccess() {
		log.info("Successfully unregistered with Sip Server");
		registered = false;
	}
	
	public void setCallStreamFactory(CallStreamFactory csf) {
		callStreamFactory = csf;
	}
	
	public void setClientConnectionManager(ClientConnectionManager ccm) {
		clientConnManager = ccm;
	}

    public void updateVideoStatus(String userId, boolean present) {
        CallAgent ca = callManager.get(userId);
        if(ca != null) {
            ca.updateVideoStatus(present);
        } else {
            log.debug("callAgent for {} is null", userId);
            for(CallAgent c : callManager.getAll()) {
                log.debug("{}", c.getUserId());
            }
        }
    }
}
