package org.bigbluebutton.voiceconf.sip;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceConfToListenOnlyUsersMap {
	private Map<String, ListenOnlyUser> listenOnlyUsers = new ConcurrentHashMap<String, ListenOnlyUser>();
	
	public final String voiceConf;
	
	public VoiceConfToListenOnlyUsersMap(String voiceConf) {
	  this.voiceConf = voiceConf;	
	}
	
	public void addUser(String clientId, String callerIdName) {
		listenOnlyUsers.put(clientId, new ListenOnlyUser(clientId, callerIdName, voiceConf));
	}
	
	public ListenOnlyUser removeUser(String clientId) {
		return listenOnlyUsers.remove(clientId);
	}
	
	public int numUsers() {
		return listenOnlyUsers.size();
	}

	public List<String> getUsers(String voiceConf){
		List<String> users = new ArrayList<String>();
		for(Iterator<String> i = listenOnlyUsers.keySet().iterator(); i.hasNext(); ) {
			users.add(i.next());
		}
		return users;
	}
}
