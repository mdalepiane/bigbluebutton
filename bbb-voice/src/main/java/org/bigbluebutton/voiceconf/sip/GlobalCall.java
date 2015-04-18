package org.bigbluebutton.voiceconf.sip;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bigbluebutton.voiceconf.video.VideoTranscoder;
import org.red5.app.sip.codecs.Codec;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class GlobalCall {
    private static final Logger log = Red5LoggerFactory.getLogger( GlobalCall.class, "sip" );

    private static Set<String> globalCalls = new HashSet<String>();
    private static Map<String,String> roomToAudioStreamMap = new ConcurrentHashMap<String, String>();
    private static Map<String,Codec> roomToAudioCodecMap = new ConcurrentHashMap<String, Codec>();
    private static Map<String,KeepGlobalAudioAlive> globalAudioKeepAliverMap = new ConcurrentHashMap<String, KeepGlobalAudioAlive>();
    private static Map<String,VideoTranscoder> roomToVideoStreamMap = new ConcurrentHashMap<String, VideoTranscoder>();

    private static Map<String, VoiceConfToListenOnlyUsersMap> voiceConfToListenOnlyUsersMap = new ConcurrentHashMap<String, VoiceConfToListenOnlyUsersMap>();
    
    public static synchronized boolean reservePlaceToCreateGlobal(String roomName) {
        if (globalCalls.contains(roomName)) {
            log.debug("There's already a global call for room {}, no need to create a new one", roomName);
            return false;
        } else {
            log.debug("Reserving the place to create a global call for room {}", roomName);
            globalCalls.add(roomName);
            voiceConfToListenOnlyUsersMap.put(roomName, new VoiceConfToListenOnlyUsersMap(roomName));
            return true;
        }
    }

    public static synchronized void addGlobalAudioStream(String voiceConf, String globalAudioStreamName, Codec sipCodec, SipConnectInfo connInfo) {
        log.debug("Adding a global audio stream to room {}", voiceConf);
        roomToAudioStreamMap.put(voiceConf, globalAudioStreamName);
        roomToAudioCodecMap.put(voiceConf, sipCodec);
        KeepGlobalAudioAlive globalAudioKeepAlive = new KeepGlobalAudioAlive(connInfo.getSocket(), connInfo, sipCodec.getCodecId());
        globalAudioKeepAliverMap.put(voiceConf, globalAudioKeepAlive);
        globalAudioKeepAlive.start();
    }

    public static synchronized String getGlobalAudioStream(String voiceConf) {
        return roomToAudioStreamMap.get(voiceConf);
    }

    public static synchronized void addGlobalVideoStream(String voiceConf, VideoTranscoder globalStream) {
        log.debug("Adding a global video stream to room {} stream {}", voiceConf, globalStream.getStreamName());
        roomToVideoStreamMap.put(voiceConf, globalStream);
    }

    public static synchronized boolean existGlobalVideoStream(String voiceConf) {
        return roomToVideoStreamMap.get(voiceConf) != null;
    }

    public static synchronized void removeGlobalVideoStrem(String voiceConf) {
        log.debug("Removing global video stream from room {}", voiceConf);
        roomToVideoStreamMap.remove(voiceConf);
    }

    public static synchronized VideoTranscoder getGlobalVideoStream(String voiceConf) {
        if(roomToVideoStreamMap.containsKey(voiceConf)) {
//            return roomToVideoStreamMap.get(voiceConf).getFreeswitchToBbbStreamName();
            return roomToVideoStreamMap.get(voiceConf);
        }
        else
            return null;
    }

    public static synchronized boolean removeRoomIfUnused(String voiceConf) {
        if (voiceConfToListenOnlyUsersMap.containsKey(voiceConf) && voiceConfToListenOnlyUsersMap.get(voiceConf).numUsers() <= 0) {
            removeRoom(voiceConf);
            return true;
        } else {
            return false;
        }
    }
 
    private static void removeRoom(String voiceConf) {
        log.debug("Removing global audio and video stream of room {}", voiceConf);
        voiceConfToListenOnlyUsersMap.remove(voiceConf);
        roomToAudioStreamMap.remove(voiceConf);
        roomToAudioCodecMap.remove(voiceConf);
        KeepGlobalAudioAlive globalAudioKeepAlive = globalAudioKeepAliverMap.get(voiceConf);
        globalAudioKeepAlive.halt();
        globalAudioKeepAliverMap.remove(voiceConf);
        roomToVideoStreamMap.remove(voiceConf);
        globalCalls.remove(voiceConf);
    }

    public static synchronized void addUser(String clientId, String callerIdName, String voiceConf) {      	
    	if (voiceConfToListenOnlyUsersMap.containsKey(voiceConf)) {
    		VoiceConfToListenOnlyUsersMap map = voiceConfToListenOnlyUsersMap.get(voiceConf);
    		map.addUser(clientId, callerIdName);
    		int numUsers = map.numUsers();
    		log.debug("Adding new user to voiceConf [{}], current number of users on global stream is {}", voiceConf, numUsers);
    	}
      
    }
    
    public static synchronized ListenOnlyUser removeUser(String clientId, String voiceConf) {
    	if (voiceConfToListenOnlyUsersMap.containsKey(voiceConf)) {
    		return voiceConfToListenOnlyUsersMap.get(voiceConf).removeUser(clientId);
    	}
    	return null;
    }

    public static synchronized List<String> getListeners(String voiceConf){
        List<String> listeners;
        if(voiceConfToListenOnlyUsersMap.containsKey(voiceConf)) {
            VoiceConfToListenOnlyUsersMap map = voiceConfToListenOnlyUsersMap.get(voiceConf);
            listeners = map.getUsers(voiceConf);
        }
        else {
            listeners = new ArrayList<String>();
        }
        return listeners;
    }

    public static Codec getRoomAudioCodec(String roomName) {
        return roomToAudioCodecMap.get(roomName);
    }

    public static boolean isVideoPaused(String roomName) {
        if(roomToVideoStreamMap.containsKey(roomName)){
            return !roomToVideoStreamMap.get(roomName).isVideoPresent();
        }
        return true;
    }
}
