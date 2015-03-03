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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.scope.IScope;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;


public class CallManager {
	private static Logger log = Red5LoggerFactory.getLogger(CallManager.class, "sip");

	private final Map<String, CallAgent> calls = new ConcurrentHashMap<String, CallAgent>();
	private final Map<String, String> identifiers = new ConcurrentHashMap<String, String>();
	private final Map<String, IBroadcastStream> videoStreams = new ConcurrentHashMap<String, IBroadcastStream>();
	private final Map<String, IScope> videoScopes = new ConcurrentHashMap<String, IScope>();
	private final Map<String, String> webRTCPorts = new ConcurrentHashMap<String, String>();
	
	public CallAgent add(CallAgent ca) {
		log.debug("Creating entry (userId, callId) = (" + ca.getUserId() + ", " + ca.getCallId() + ")" );
		
		if(ca.getUserId() != null) {
			identifiers.put(ca.getUserId(), ca.getCallId());
		}
		return calls.put(ca.getCallId(), ca);
	}
	
	public CallAgent remove(String id) {
		CallAgent ca = calls.get(id);
		String userId = ca.getUserId();

		if(userId != null) {
			identifiers.remove(userId);
		}
		log.debug("Removing callAgent entry for user: " + userId);
		return calls.remove(id);
	}

	public IBroadcastStream addVideoStream(String userId, IBroadcastStream stream) {
		log.debug("Creating entry (userId, videoStream) = (" + userId + ", " + stream.getPublishedName() + ")" );
		return videoStreams.put(userId, stream);
	}

	public IBroadcastStream removeVideoStream(String userId) {
		String uid = userId;
		log.debug("Removing videoStream entry for user: "  + userId  );
		return videoStreams.remove(uid);
	}

	public IScope addVideoScope(String userId, IScope scope) {
		log.debug("Creating entry (userId, scope) = (" + userId + ", " + scope.getName() + ")" );
		return videoScopes.put(userId, scope);
	}

	public IScope removeVideoScope(String userId) {
		String uid = userId;
		log.debug("Removing scope entry for user: "  + userId  );
		return videoScopes.remove(uid);
	}

	public String addWebRTCPorts(String userId, String ports) {
		//format: localPort=xxxx,remotePort=xxxx
		log.debug("Creating entry (userId, ports) = (" + userId + ", " + ports + ")" );
		return webRTCPorts.put(userId, ports);
	}

	public String removeWebRTCPorts(String userId) {
		String uid = userId;
		log.debug("Removing videoStream entry for user: "  + userId  );
		return webRTCPorts.remove(uid);
	}

	public CallAgent removeByUserId(String userId) {
		String uid = userId;
		String id;

		if( (id = identifiers.get(uid)) == null )
			return null;
		else {
			identifiers.remove(uid);
			return calls.remove(id);
		}
	}
	
	public CallAgent get(String id) {
		return calls.get(id);
	}

	public CallAgent getByUserId(String userId) {

		//first we retrieve the 'clientId' using the 'userId' as key, then - with the 'clientId' - we retrieve the CallAgent
		//this is necessary to get the CallAgent in order to start the sip video publish.

		String uid = userId;
		String id;

		if( (id = identifiers.get(uid)) == null )
			return null;
		else {
			log.debug("[Video context] clientId retrieved with the userid " + uid + " ==> " + id);
			return calls.get(id);
		}
	}

	public IBroadcastStream getVideoStream(String userId) {
		String uid = userId;
		log.debug("[Video context] stream retrieved for the userid " + uid);
			return videoStreams.get(uid);
	}

	public IScope getVideoScope(String userId) {
		String uid = userId;
		log.debug("[Video context] scope retrieved for the userid " + uid);
			return videoScopes.get(uid);
	}
	
	public Collection<CallAgent> getAll() {
		return calls.values();
	}
}
