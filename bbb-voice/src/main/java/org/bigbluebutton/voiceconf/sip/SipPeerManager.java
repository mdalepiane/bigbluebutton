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

import org.slf4j.Logger;
import org.zoolu.sip.provider.SipStack;
import org.bigbluebutton.voiceconf.messaging.IMessagingService;
import org.bigbluebutton.voiceconf.red5.CallStreamFactory;
import org.bigbluebutton.voiceconf.red5.ClientConnectionManager;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;

import java.util.*;

/**
 * Manages all connections to the sip voice conferencing server.
 * @author Richard Alam
 *
 */
public final class SipPeerManager {
	private static final Logger log = Red5LoggerFactory.getLogger( SipPeerManager.class, "sip" );
	
	private ClientConnectionManager clientConnManager;
	private CallStreamFactory callStreamFactory;
	private IMessagingService messagingService;
	
    private Map<String, SipPeer> sipPeers;
    private int sipStackDebugLevel = 8;
    private int sipRemotePort = 5060;
    
    public SipPeerManager() {
        sipPeers = Collections.synchronizedMap(new HashMap<String, SipPeer>());
    }

    public void createSipPeer(String peerId, String clientRtpIp, String host, int sipPort, 
			int startAudioPort, int stopAudioPort, int startVideoPort, int stopVideoPort) {
    	SipPeer sipPeer = new SipPeer(peerId, clientRtpIp, host, sipPort, startAudioPort, stopAudioPort, startVideoPort, stopVideoPort, messagingService);
    	sipPeer.setClientConnectionManager(clientConnManager);
    	sipPeer.setCallStreamFactory(callStreamFactory);
    	sipPeers.put(peerId, sipPeer);    	
    }
    
    public void register(String peerId, String username, String password) throws PeerNotFoundException {
    	SipPeer sipPeer = sipPeers.get(peerId);
    	if (sipPeer == null) throw new PeerNotFoundException("Can't find sip peer " + peerId);
  		sipPeer.register(username, password);
    }
        
    public void call(String peerId, String clientId, String callerName, String destination) throws PeerNotFoundException {
    	SipPeer sipPeer = sipPeers.get(peerId);
    	if (sipPeer == null) throw new PeerNotFoundException("Can't find sip peer " + peerId);
    	sipPeer.call(clientId, callerName, destination);
    }

    public void unregister(String userid) {
    	SipPeer sipUser = sipPeers.get(userid);
    	if (sipUser != null) {
    		sipUser.unregister();
    	}
    }
    
    public void hangup(String peerId, String clientId) throws PeerNotFoundException {
    	SipPeer sipPeer = sipPeers.get(peerId);
    	if (sipPeer == null) throw new PeerNotFoundException("Can't find sip peer " + peerId);
    	sipPeer.hangup(clientId);
    }

    
    public void startBbbToFreeswitchAudioStream(String peerId, String userId, String clientId, IBroadcastStream broadcastStream, IScope scope) {
    	SipPeer sipUser = sipPeers.get(peerId);
        log.debug("Start Audio Stream SipPeer");
    	if (sipUser != null) {
            sipUser.startBbbToFreeswitchAudioStream(clientId,userId, broadcastStream, scope);
    	}
    }
    
    public void stopBbbToFreeswitchAudioStream(String peerId, String clientId, IBroadcastStream broadcastStream, IScope scope) {
    	SipPeer sipUser = sipPeers.get(peerId);
    	if (sipUser != null) {
    		sipUser.stopBbbToFreeswitchAudioStream(clientId, broadcastStream, scope);
    	}
    }

    public void startBbbToFreeswitchVideoStream(String peerId, String userId, IBroadcastStream broadcastStream, IScope scope) {
        SipPeer sipUser = sipPeers.get(peerId);
        if (sipUser != null) {
            sipUser.startBbbToFreeswitchVideoStream(userId, broadcastStream, scope);
        }
    }
    
    public void stopBbbToFreeswitchVideoStream(String peerId, String userId, IBroadcastStream broadcastStream, IScope scope) {
        SipPeer sipUser = sipPeers.get(peerId);
        if (sipUser != null) {
            sipUser.stopBbbToFreeswitchVideoStream(userId, broadcastStream, scope);
        }
    }

    public void startBbbToFreeswitchWebRTCVideoStream(String peerId, String userId, String ip, String remoteVideoPort, String localVideoPort) throws PeerNotFoundException {
        SipPeer sipUser = sipPeers.get(peerId);
        if (sipUser != null) {
            sipUser.startBbbToFreeswitchWebRTCVideoStream(userId, ip, remoteVideoPort,localVideoPort);
        }else throw new PeerNotFoundException("Can't find sip peer " + peerId);
    }

    public void stopBbbToFreeswitchWebRTCVideoStream(String peerId, String userId) throws PeerNotFoundException {
        SipPeer sipUser = sipPeers.get(peerId);
        if (sipUser != null) {
            sipUser.stopBbbToFreeswitchWebRTCVideoStream(userId);
        }else throw new PeerNotFoundException("Can't find sip peer " + peerId);
    }

    public boolean startFreeswitchToBbbVideoStream(String peerId, String userId) {
        SipPeer sipUser = sipPeers.get(peerId);
        if(sipUser != null) {
            return sipUser.startFreeswitchToBbbVideoStream(userId);
        } else
            log.debug("sipUser for {} is null", peerId);
        return false;
    }

    public String getStreamType(String peerId, String clientId, String streamName) {
        SipPeer sipUser = sipPeers.get(peerId);
        if (sipUser != null) {
            return sipUser.getStreamType(clientId, streamName);
        }
        else
        {
            log.debug("[SipPeerManager] Invalid peerId");
            return null;
        }
    }

    public boolean isAudioStream(String peerId, String clientId, IBroadcastStream broadcastStream) {
        SipPeer sipUser = sipPeers.get(peerId);
        if (sipUser != null) {
            return sipUser.isAudioStream(clientId, broadcastStream);
        }
        else
            return false;
    }

    public boolean isVideoStream(String peerId, String clientId, IBroadcastStream broadcastStream) {
        SipPeer sipUser = sipPeers.get(peerId);
        if (sipUser != null) {
            return sipUser.isVideoStream(clientId, broadcastStream);
        }
        else
            return false;
    }
    
    private void remove(String userid) {
    	log.debug("Number of SipUsers in Manager before remove {}", sipPeers.size());
        sipPeers.remove(userid);
    }

    public void connectToGlobalStream(String peerId, String clientId, String callerIdName, String destination) {
    	SipPeer sipUser = sipPeers.get(peerId);
    	if (sipUser != null) {
    		sipUser.connectToGlobalStream(clientId, callerIdName, destination);
    	}
    }

    public void close(String userid) {
    	SipPeer sipUser = sipPeers.get(userid);
    	if (sipUser != null) {
    		sipUser.close();
    		remove(userid);
    	}
    }

    public void destroyAllSessions() {
    	Collection<SipPeer> sipUsers = sipPeers.values();
        SipPeer sp;

        for (Iterator<SipPeer> iter = sipUsers.iterator(); iter.hasNext();) {
            sp = (SipPeer) iter.next();
            sp.close();
            sp = null;
        }

        sipPeers = new HashMap<String, SipPeer>();
    }

	public void setSipStackDebugLevel(int sipStackDebugLevel) {
		this.sipStackDebugLevel = sipStackDebugLevel;
		SipStack.init();
        SipStack.debug_level = sipStackDebugLevel;
        SipStack.log_path = "log"; 
	}
	
	public void setSipRemotePort(int port) {
		this.sipRemotePort =  port;
		SipStack.init();
		SipStack.default_port = sipRemotePort;
	}
	
	public void setCallStreamFactory(CallStreamFactory csf) {
		callStreamFactory = csf;
	}
	
	public void setClientConnectionManager(ClientConnectionManager ccm) {
		clientConnManager = ccm;
	}
	
	public void setMessagingService(IMessagingService service) {
		messagingService = service;
	}

    public void updateVideoStatus(String peerId, String userId, boolean present) {
        SipPeer sipUser = sipPeers.get(peerId);
        if(sipUser != null) {
            sipUser.updateVideoStatus(userId, present);
        } else
            log.debug("sipUser for {} is null", peerId);
    }
}
