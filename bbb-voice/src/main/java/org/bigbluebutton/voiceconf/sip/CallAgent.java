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

import org.zoolu.sip.call.*;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipStack;
import org.zoolu.sip.message.*;
import org.zoolu.sdp.*;
import org.bigbluebutton.voiceconf.messaging.IMessagingService;
import org.bigbluebutton.voiceconf.red5.CallStreamFactory;
import org.bigbluebutton.voiceconf.red5.ClientConnectionManager;
import org.bigbluebutton.voiceconf.red5.media.CallStream;
import org.bigbluebutton.voiceconf.red5.media.CallStreamObserver;
import org.bigbluebutton.voiceconf.red5.media.StreamException;
import org.bigbluebutton.voiceconf.util.StackTraceUtil;
import org.red5.app.sip.codecs.Codec;
import org.red5.app.sip.codecs.CodecUtils;
import org.red5.app.sip.codecs.H264Codec;
import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Vector;

import java.util.HashMap;

public class CallAgent extends CallListenerAdapter implements CallStreamObserver  {
    private static Logger log = Red5LoggerFactory.getLogger(CallAgent.class, "sip");
    
    private final SipPeerProfile userProfile;
    private final SipProvider sipProvider;
    private final String clientRtpIp;
    private ExtendedCall call;
    private CallStream audioCallStream; 
    private CallStream videoCallStream;    
    private String localSession = null;
    private Codec sipAudioCodec = null;
    private Codec sipVideoCodec = null;    
    private CallStreamFactory callStreamFactory;
    private ClientConnectionManager clientConnManager; 
    private final String clientId;
    private final ConferenceProvider portProvider;
    private DatagramSocket localAudioSocket;
    private DatagramSocket localVideoSocket;
    private String _callerName;
    private String _destination;
    private Boolean listeningToGlobal = false;
    private IMessagingService messagingService;

    private HashMap<String, String> streamTypeManager = null;

    private String destinationPrefix;

    private ProcessMonitor processMonitor = null;
    
    private enum CallState {
    	UA_IDLE(0), UA_INCOMING_CALL(1), UA_OUTGOING_CALL(2), UA_ONCALL(3);    	
    	private final int state;
    	CallState(int state) {this.state = state;}
    	private int getState() {return state;}
    }

    private CallState callState;

    public String getDestination() {
        return _destination;
    }

    public CallAgent(String sipClientRtpIp, SipProvider sipProvider, SipPeerProfile userProfile,
    		ConferenceProvider portProvider, String clientId, IMessagingService messagingService) {
        this.sipProvider = sipProvider;
        this.clientRtpIp = sipClientRtpIp;
        this.userProfile = userProfile;
        this.portProvider = portProvider;
        this.clientId = clientId;
        this.messagingService = messagingService;

        if(this.streamTypeManager == null)
            this.streamTypeManager = new HashMap<String, String>();
    }
    
    public String getCallId() {
    	return clientId;
    }
    
    private void initSessionDescriptor() {        
        log.debug("initSessionDescriptor => userProfile.videoPort = " + userProfile.videoPort + " userProfile.audioPort =" + userProfile.audioPort);
        SessionDescriptor newSdp = SdpUtils.createInitialSdp(userProfile.username, 
        		this.clientRtpIp, userProfile.audioPort, 
        		userProfile.videoPort, userProfile.audioCodecsPrecedence );
        localSession = newSdp.toString();        
        log.debug("localSession Descriptor = " + localSession );
    }

    public Boolean isListeningToGlobal() {
        return listeningToGlobal;
    }

    public void call(String callerName, String destination) {
    	_callerName = callerName;
    	_destination = destination;
    	log.debug("{} making a call to {}", callerName, destination);  
    	try {

			localAudioSocket = getLocalAudioSocket();
			userProfile.audioPort = localAudioSocket.getLocalPort();

            localVideoSocket = getLocalVideoSocket();  
            userProfile.videoPort = localVideoSocket.getLocalPort();

		} catch (Exception e) {
			log.debug("{} failed to allocate local port for call to {}. Notifying client that call failed.", callerName, destination); 
			notifyListenersOnOutgoingCallFailed();
			return;
		}    	
    	
		setupCallerDisplayName(callerName, destination);	
    	userProfile.initContactAddress(sipProvider);        
        initSessionDescriptor();
        
    	callState = CallState.UA_OUTGOING_CALL;
    	
        call = new ExtendedCall(sipProvider, userProfile.fromUrl, 
                userProfile.contactUrl, userProfile.username,
                userProfile.realm, userProfile.passwd, this);  
        
        // In case of incomplete url (e.g. only 'user' is present), 
        // try to complete it.       
        destination = sipProvider.completeNameAddress(destination).toString();
        log.debug("call {}", destination);  
        if (userProfile.noOffer) {
            call.call(destination);
        } else {
            call.call(destination, localSession);
        }
    }

    private void setupCallerDisplayName(String callerName, String destination) {
        destinationPrefix = destination;
    	String fromURL = "\"" + callerName + "\" <sip:" + destination + "@" + portProvider.getHost() + ">";
    	userProfile.username = callerName;
    	userProfile.fromUrl = fromURL;
		userProfile.contactUrl = "sip:" + destination + "@" + sipProvider.getViaAddress();
        if (sipProvider.getPort() != SipStack.default_port) {
            userProfile.contactUrl += ":" + sipProvider.getPort();
        }

        if(callerName.startsWith("GLOBAL_AUDIO")) {
            userProfile.userID = callerName;
        }
        else {
            userProfile.userID = callerName.substring( 0, callerName.indexOf("-") );
        }
        log.debug("userID: " + userProfile.userID);
    }
    
    /** Closes an ongoing, incoming, or pending call */
    public void hangup() {
    	if (callState == CallState.UA_IDLE) return;
    	log.debug("hangup");
    	if (listeningToGlobal) {
    		log.debug("Hanging up of a call connected to the global audio stream");
    		notifyListenersOfOnCallClosed();
    	} else {
    		closeStreams();
    		if (call != null) call.hangup();
    	}
    	callState = CallState.UA_IDLE; 
    }

    private DatagramSocket getLocalAudioSocket() throws Exception {
    	DatagramSocket socket = null;
    	boolean failedToGetSocket = true;
    	StringBuilder failedPorts = new StringBuilder("Failed ports: ");
    	
    	for (int i = portProvider.getStartAudioPort(); i <= portProvider.getStopAudioPort(); i++) {
    		int freePort = portProvider.getFreeAudioPort();
    		try {    			
        		socket = new DatagramSocket(freePort);
        		failedToGetSocket = false;
        		log.info("Successfully setup local audio port {}. {}", freePort, failedPorts);
        		break;
    		} catch (SocketException e) {
    			failedPorts.append(freePort + ", ");   			
    		}
    	}
    	
    	if (failedToGetSocket) {
			log.warn("Failed to setup local audio port {}.", failedPorts); 
    		throw new Exception("Exception while initializing CallStream");
    	}
    	
    	return socket;
    }

    private boolean isGlobalStream() {
        return (_callerName != null && _callerName.startsWith("GLOBAL_AUDIO_"));
    }

    private DatagramSocket getLocalVideoSocket() throws Exception {
        DatagramSocket socket = null;
        boolean failedToGetSocket = true;
        StringBuilder failedPorts = new StringBuilder("Failed ports: ");
        
        for (int i = portProvider.getStartVideoPort(); i <= portProvider.getStopVideoPort(); i++) {
            int freePort = portProvider.getFreeVideoPort();
            try {               
                socket = new DatagramSocket(freePort);
                failedToGetSocket = false;
                log.info("Successfully setup local VIDEO port {}. {}", freePort, failedPorts);
                break;
            } catch (SocketException e) {
                failedPorts.append(freePort + ", ");            
            }
        }
        
        if (failedToGetSocket) {
            log.warn("Failed to setup local VIDEO port {}.", failedPorts); 
            throw new Exception("Exception while initializing CallStream");
        }
        
        return socket;
    }
    
    private void createStreams() {
        boolean audioStreamCreatedSuccesfully = false;

        SessionDescriptor localSdp = new SessionDescriptor(call.getLocalSessionDescriptor());        
        SessionDescriptor remoteSdp = new SessionDescriptor(call.getRemoteSessionDescriptor());
        String remoteMediaAddress = SessionDescriptorUtil.getRemoteMediaAddress(remoteSdp);

        if (audioCallStream == null) {            
            int remoteAudioPort = SessionDescriptorUtil.getRemoteMediaPort(remoteSdp, SessionDescriptorUtil.SDP_MEDIA_AUDIO);
            int localAudioPort = SessionDescriptorUtil.getLocalMediaPort(localSdp, SessionDescriptorUtil.SDP_MEDIA_AUDIO);
            audioStreamCreatedSuccesfully = createAudioStream(remoteMediaAddress,localAudioPort,remoteAudioPort); 
                    	
        }else log.debug("AUDIO application is already running.");
        
        if (videoCallStream == null) {        
            int remoteVideoPort = SessionDescriptorUtil.getRemoteMediaPort(remoteSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO);
            int localVideoPort = SessionDescriptorUtil.getLocalMediaPort(localSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO);
            if(isGlobalStream()) {
                // Only global stream create the video stream here to receive video from FreeSWITCH
                createVideoStream(remoteMediaAddress,localVideoPort,remoteVideoPort);
                log.debug("VIDEO stream created");
            }

        }else log.debug("VIDEO application is already running.");

        if(audioStreamCreatedSuccesfully && !isGlobalStream()) {

            String userSenderAudioStream = audioCallStream.getBbbToFreeswitchStreamName();
            String userReceiverAudioStream = audioCallStream.getFreeswitchToBbbStreamName();

            notifyListenersOnCallConnected(userSenderAudioStream, userReceiverAudioStream);
        }
    }

    private boolean createAudioStream(String remoteMediaAddress, int localAudioPort, int remoteAudioPort) {

       SipConnectInfo connInfo = new SipConnectInfo(localAudioSocket, remoteMediaAddress, remoteAudioPort);
       try {

            localAudioSocket.connect(InetAddress.getByName(remoteMediaAddress), remoteAudioPort);

            if (userProfile.audio && localAudioPort != 0 && remoteAudioPort != 0) {
                if ((audioCallStream == null) && (sipAudioCodec != null)) {                  
                    try {
                        log.debug("Creating AUDIO stream: [localAudioPort=" + localAudioPort + ",remoteAudioPort=" + remoteAudioPort + "]");
                        audioCallStream = callStreamFactory.createCallStream(sipAudioCodec, connInfo, CallStream.MEDIA_TYPE_AUDIO);
                        audioCallStream.addCallStreamObserver(this);
                        audioCallStream.start();
                        String streamName = audioCallStream.getBbbToFreeswitchStreamName();

                        if(!streamTypeManager.containsKey(streamName))
                        {
                            streamTypeManager.put(streamName, CallStream.MEDIA_TYPE_AUDIO);
                            log.debug("[CallAgent] streamTypeManager adding audio stream {} for {}", streamName, clientId);
                        }

                        if (isGlobalStream())
                        {
                        	GlobalCall.addGlobalAudioStream(_destination, audioCallStream.getFreeswitchToBbbStreamName(), sipAudioCodec, connInfo);
                        }

                        return true;

                    } catch (Exception e) {
                        log.error("Failed to create AUDIO Call Stream.");
                        System.out.println(StackTraceUtil.getStackTrace(e));
                    }                
                }
            }

        } catch (UnknownHostException e1) {
            log.error("Failed to connect for AUDIO Stream.");
            log.error(StackTraceUtil.getStackTrace(e1));
        }

        return false;

    }

    private boolean createVideoStream(String remoteMediaAddress, int localVideoPort, int remoteVideoPort) {

       SipConnectInfo connInfo = new SipConnectInfo(localVideoSocket, remoteMediaAddress, remoteVideoPort);
       try {
            localVideoSocket.connect(InetAddress.getByName(remoteMediaAddress), remoteVideoPort);        
            
            if (userProfile.video && localVideoPort != 0 && remoteVideoPort != 0) {
                if ((videoCallStream == null) && (sipVideoCodec != null)) {                  
                    try {
                        log.debug("Creating VIDEO stream: [localVideoPort=" + localVideoPort + ",remoteVideoPort=" + remoteVideoPort + "]");
                        videoCallStream = callStreamFactory.createCallStream(sipVideoCodec, connInfo, CallStream.MEDIA_TYPE_VIDEO);                                                
                        videoCallStream.addCallStreamObserver(this);
                        videoCallStream.start();
                        String streamName = videoCallStream.getBbbToFreeswitchStreamName();
                        if(!streamTypeManager.containsKey(streamName))
                        {
                            streamTypeManager.put(streamName, CallStream.MEDIA_TYPE_VIDEO);
                            log.debug("[CallAgent] streamTypeManager adding video stream {} for {}", streamName, clientId);
                        }

                        if (isGlobalStream())
                        {
                            GlobalCall.addGlobalVideoStream(_destination, videoCallStream, connInfo);
                        }

                        return true;        
                            
                    } catch (Exception e) {
                        log.error("Failed to create VIDEO Call Stream.");
                        System.out.println(StackTraceUtil.getStackTrace(e));
                    }                
                }
            }

        } catch (UnknownHostException e1) {
            log.error("Failed to connect for VIDEO Stream.");
            log.error(StackTraceUtil.getStackTrace(e1));
        }

        return false;
    }

        
   public void startBbbToFreeswitchAudioStream(IBroadcastStream broadcastStream, IScope scope) {
    	try {
			audioCallStream.startBbbToFreeswitchStream(broadcastStream, scope);

		} catch (StreamException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
       
    public void stopBbbToFreeswitchAudioStream(IBroadcastStream broadcastStream, IScope scope) {
    	if (audioCallStream != null) {
    		audioCallStream.stopBbbToFreeswitchStream(broadcastStream, scope);   	
    		String streamName = audioCallStream.getBbbToFreeswitchStreamName();
    		if(streamTypeManager.containsKey(streamName))
    		    streamTypeManager.remove(streamName);
    	} else {
    		log.info("Can't stop talk stream as stream may have already stopped.");
    	}
    }
    
     public void startBbbToFreeswitchVideoStream(IBroadcastStream broadcastStream, IScope scope) {
        try {
            SessionDescriptor remoteSdp = new SessionDescriptor(call.getRemoteSessionDescriptor());
        	SessionDescriptor localSdp = new SessionDescriptor(call.getLocalSessionDescriptor());
            
            int remotePort = SessionDescriptorUtil.getRemoteMediaPort(remoteSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO);
            int localPort = SessionDescriptorUtil.getRemoteMediaPort(localSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO);
            
        	Codec codec = new H264Codec();
        	String ip = Red5.getConnectionLocal().getHost();

            String inputLive = "rtmp://" + ip + "/video/" + scope.getName() + "/"
                                + broadcastStream.getPublishedName() + " live=1";
            String output = "rtp://" + ip + ":" + remotePort + "?localport=" + localPort;

            FFmpegCommand ffmpeg = new FFmpegCommand();
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
        	localVideoSocket.close();

            log.debug("Preparing FFmpeg process monitor");

            processMonitor = new ProcessMonitor(command);
            processMonitor.start();

            // videoCallStream.startBbbToFreeswitchStream(broadcastStream, scope);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public void stopBbbToFreeswitchVideoStream(IBroadcastStream broadcastStream, IScope scope) {
        if(processMonitor != null) {
            processMonitor.destroy();
        }
    }

    private void closeStreams() {        
    	log.debug("Shutting down the AUDIO stream...");         
        if (audioCallStream != null) {
        	audioCallStream.stopFreeswitchToBbbStream();
        	audioCallStream = null;
        } else {
        	log.debug("Can't shutdown AUDIO stream: already NULL");
        }

        log.debug("Shutting down the VIDEO stream...");         
        if (videoCallStream != null) {
            videoCallStream.stopFreeswitchToBbbStream();
            videoCallStream = null;
        } else {
            log.debug("Can't shutdown VIDEO stream: already NULL");
        }

        if(processMonitor != null) {
            processMonitor.destroy();
            processMonitor = null;
        }
    }

    
    public void connectToGlobalStream(String clientId, String callerIdName, String voiceConf) {
        listeningToGlobal = true;
        _destination = voiceConf;

        String globalAudioStreamName = GlobalCall.getGlobalAudioStream(voiceConf);
        String globalVideoStreamName = GlobalCall.getGlobalVideoStream(voiceConf);
        while (globalAudioStreamName.equals(null) || globalVideoStreamName.equals(null)) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
            }
            globalAudioStreamName = GlobalCall.getGlobalAudioStream(voiceConf);
            globalVideoStreamName = GlobalCall.getGlobalVideoStream(voiceConf);
        }
		    
        GlobalCall.addUser(clientId, callerIdName, _destination);
        sipAudioCodec = GlobalCall.getRoomAudioCodec(voiceConf);
        sipVideoCodec = GlobalCall.getRoomVideoCodec(voiceConf);
        callState = CallState.UA_ONCALL;
        notifyListenersOnCallConnected("", globalAudioStreamName);
        log.info("User is has connected to global audio, user=[" + callerIdName + "] voiceConf = [" + voiceConf + "]");
        messagingService.userConnectedToGlobalAudio(voiceConf, callerIdName);
        userProfile.userID = callerIdName;
    }


    public String getStreamType(String streamName) {
        if(streamTypeManager.containsKey(streamName))
            return streamTypeManager.get(streamName);
        else
        {
            log.debug("[CallAgent] streamTypeManager does not contain " + streamName);
            return null;
        }
    }

    public boolean isAudioStream(IBroadcastStream broadcastStream) {
        String streamName = broadcastStream.getPublishedName();
        if(streamTypeManager.containsKey(streamName))
            return streamTypeManager.get(streamName).equals(CallStream.MEDIA_TYPE_AUDIO);
        else
            return false;
    }

    public boolean isVideoStream(IBroadcastStream broadcastStream) {
        String streamName = broadcastStream.getPublishedName();
        if(streamTypeManager.containsKey(streamName))
            return streamTypeManager.get(streamName).equals(CallStream.MEDIA_TYPE_VIDEO);
        else
            return false;
    }

    // ********************** Call callback functions **********************
    private void createAudioCodec(SessionDescriptor newSdp) {
    	sipAudioCodec = SdpUtils.getNegotiatedAudioCodec(newSdp);
    }

    private void createVideoCodec(SessionDescriptor newSdp) {
        sipVideoCodec = SdpUtils.getNegotiatedVideoCodec(newSdp);
    }
        
    private void setupSdpAndCodec(String sdp) {
    	SessionDescriptor remoteSdp = new SessionDescriptor(sdp);
        SessionDescriptor localSdp = new SessionDescriptor(localSession);
        
        log.debug("localSdp = " + localSdp.toString() + ".");
        log.debug("remoteSdp = " + remoteSdp.toString() + ".");
        
        // First we need to make payloads negotiation so the related attributes can be then matched.
        SessionDescriptor newSdp = SdpUtils.makeMediaPayloadsNegotiation(localSdp, remoteSdp);        
        createAudioCodec(newSdp);
        createVideoCodec(newSdp);
        
        // Now we complete the SDP negotiation informing the selected 
        // codec, so it can be internally updated during the process.
        SdpUtils.completeSdpNegotiation(newSdp, localSdp, remoteSdp);
        localSession = newSdp.toString();
        
        log.debug("newSdp = " + localSession + "." );
        
        // Finally, we use the "newSdp" and "remoteSdp" to initialize the lasting codec informations.
        CodecUtils.initSipAudioCodec(sipAudioCodec, userProfile.audioDefaultPacketization, 
                userProfile.audioDefaultPacketization, newSdp, remoteSdp);

        //Init the video codec? we don't know yet...
        //CodecUtils.initSipVideoCodec(sipVideoCodec, userProfile.audioDefaultPacketization, 
                //userProfile.audioDefaultPacketization, newSdp, remoteSdp);

    }


    /** Callback function called when arriving a 2xx (call accepted) 
     *  The user has managed to join the conference.
     */ 
    public void onCallAccepted(Call call, String sdp, Message resp) {        
    	log.debug("Received 200/OK. So user has successfully joined the conference.");        
    	if (!isCurrentCall(call)) return;
        callState = CallState.UA_ONCALL;

        setupSdpAndCodec(sdp);

        if (userProfile.noOffer) {
            // Answer with the local sdp.
            call.ackWithAnswer(localSession);
        }

        createStreams();
    }

    /** Callback function called when arriving an ACK method (call confirmed) */
    public void onCallConfirmed(Call call, String sdp, Message ack) {
    	log.debug("Received ACK. Hmmm...is this for when the server initiates the call????");
        
    	if (!isCurrentCall(call)) return;        
        callState = CallState.UA_ONCALL;
        createStreams();
    }

    /** Callback function called when arriving a 4xx (call failure) */
    public void onCallRefused(Call call, String reason, Message resp) {        
    	log.debug("Call has been refused.");        
    	if (!isCurrentCall(call)) return;
        callState = CallState.UA_IDLE;
        notifyListenersOnOutgoingCallFailed();
    }

    /** Callback function called when arriving a 3xx (call redirection) */
    public void onCallRedirection(Call call, String reason, Vector contact_list, Message resp) {        
    	log.debug("onCallRedirection");
        
    	if (!isCurrentCall(call)) return;
        call.call(((String) contact_list.elementAt(0)));
    }


    /**
     * Callback function that may be overloaded (extended). Called when arriving a CANCEL request
     */
    public void onCallCanceling(Call call, Message cancel) {
    	log.error("Server shouldn't cancel call...or does it???");
        
    	if (!isCurrentCall(call)) return; 
        
        log.debug("Server has CANCEL-led the call.");
        callState = CallState.UA_IDLE;
        notifyListenersOfOnIncomingCallCancelled();
    }

    private void notifyListenersOnCallConnected(String userSenderAudioStream, String userReceiverAudioStream) {
        log.debug("notifyListenersOnCallConnected for {}", clientId);
        String audioCodec = "";

        if(sipAudioCodec != null)
            audioCodec = sipAudioCodec.getCodecName();

        clientConnManager.joinConferenceSuccess(clientId, userSenderAudioStream, userReceiverAudioStream, audioCodec);
    }    


    private void notifyListenersOnOutgoingCallFailed() {
    	log.debug("notifyListenersOnOutgoingCallFailed for {}", clientId);
    	clientConnManager.joinConferenceFailed(clientId);
    	cleanup();
    }

    
    private void notifyListenersOfOnIncomingCallCancelled() {
    	log.debug("notifyListenersOfOnIncomingCallCancelled for {}", clientId);
    }
    
    private void notifyListenersOfOnCallClosed() {
        if (callState == CallState.UA_IDLE) return;

        if(isGlobalStream()) {
            for(Iterator<String> i = GlobalCall.getListeners(_destination).iterator(); i.hasNext();) {
                String userId = i.next();
                log.debug("notifyListenersOfOnCallClosed for {}", userId);
                clientConnManager.leaveConference(userId);
            }
        }
        else {
            log.debug("notifyListenersOfOnCallClosed for {}", clientId);
            clientConnManager.leaveConference(clientId);
        }
        cleanup();
    }

    private void notifyListenersOfOnCallPaused() {
        if(isGlobalStream()) {
           for(Iterator<String> i = GlobalCall.getListeners(_destination).iterator(); i.hasNext(); ) {
                String userId = i.next();
                log.debug("notifyListenersOfOnCallPaused for {}", userId);
                clientConnManager.pausedVideo(userId);
           }
        }
        else {
            log.debug("notifyListenersOfOnCallPaused for {}", clientId);
            clientConnManager.pausedVideo(clientId);
        }
    }

    private void notifyListenersOfOnCallStarted(String videoStream) {
        if(isGlobalStream()) {
            for(Iterator<String> i = GlobalCall.getListeners(_destination).iterator(); i.hasNext(); ) {
                String userId = i.next();
                log.debug("notifyListenersOfOnCallRestarted for {}", userId);
                clientConnManager.startedVideo(userId, videoStream);
            }
        }
        else {
            log.debug("notifyListenersOfOnCallRestarted for {}", clientId);
            clientConnManager.startedVideo(clientId, videoStream);
        }
    }

    private void cleanup() {
        if (localAudioSocket == null) return;

        log.debug("Closing local audio port {}", localAudioSocket.getLocalPort());
        if (!listeningToGlobal) {
            localAudioSocket.close();
        }
    }

    /** Callback function called when arriving a BYE request */
    public void onCallClosing(Call call, Message bye) {
    	log.info("Received a BYE from the other end telling us to hangup.");
        
    	if (!isCurrentCall(call)) return;               
        closeStreams();
        notifyListenersOfOnCallClosed();
        callState = CallState.UA_IDLE;

        // Reset local sdp for next call.
        initSessionDescriptor();
    }


    /**
     * Callback function called when arriving a response after a BYE request
     * (call closed)
     */
    public void onCallClosed(Call call, Message resp) {
    	log.debug("onCallClosed");
        
    	if (!isCurrentCall(call)) return;         
        log.debug("CLOSE/OK.");
        
        notifyListenersOfOnCallClosed();
        callState = CallState.UA_IDLE;
    }


    /** Callback function called when the invite expires */
    public void onCallTimeout(Call call) {        
    	log.debug("onCallTimeout");
        
    	if (!isCurrentCall(call)) return; 
        
        log.debug("NOT FOUND/TIMEOUT.");
        callState = CallState.UA_IDLE;

        notifyListenersOnOutgoingCallFailed();
    }

    public void onCallStreamStopped() {
    	log.info("Call stream has been stopped");
    	notifyListenersOfOnCallClosed();
    }

    public void onCallStreamPaused() {
        log.info("Call stream has been paused");
        notifyListenersOfOnCallPaused();
    }

    public void onCallStreamStarted() {
        log.info("Call stream has been started");
        String videoStream = videoCallStream.getFreeswitchToBbbStreamName();
        notifyListenersOfOnCallStarted(videoStream);
    }
    
    private boolean isCurrentCall(Call call) {
    	return this.call == call;
    }
    
    public void setCallStreamFactory(CallStreamFactory csf) {
    	this.callStreamFactory = csf;
    }
    
	public void setClientConnectionManager(ClientConnectionManager ccm) {
		clientConnManager = ccm;
	}

    public String getUserId() {
        return userProfile.userID;
    }

    @Override
    public void onFirRequest() {
        log.debug("Sending FIR request to FreeSwitch..."); 
        
        Message msg = MessageFactory.createRequest(
                sipProvider
                , SipMethods.INFO
                , sipProvider.completeNameAddress(destinationPrefix)
                , sipProvider.completeNameAddress(userProfile.fromUrl)
                , ""); // no way to pass content-type, will set empty message for now

        msg.setBody("application/media_control+xml"
                ,       "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        " <media_control>\n" +
                        "  <vc_primitive>\n" +
                        "   <to_encoder>\n" +
                        "    <picture_fast_update>\n" +
                        "    </picture_fast_update>\n" +
                        "   </to_encoder>\n" +
                        "  </vc_primitive>\n" +
                        " </media_control>\n");


        sipProvider.sendMessage(msg);
               
    }   
}
