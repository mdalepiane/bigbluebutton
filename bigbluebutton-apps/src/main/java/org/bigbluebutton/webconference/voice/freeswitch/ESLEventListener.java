package org.bigbluebutton.webconference.voice.freeswitch;


import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bigbluebutton.webconference.voice.events.ConferenceEventListener;
import org.bigbluebutton.webconference.voice.events.VideoFloorChangedEvent;
import org.bigbluebutton.webconference.voice.events.VideoPausedEvent;
import org.bigbluebutton.webconference.voice.events.VideoResumedEvent;
import org.bigbluebutton.webconference.voice.events.VoiceUserJoinedEvent;
import org.bigbluebutton.webconference.voice.events.VoiceUserLeftEvent;
import org.bigbluebutton.webconference.voice.events.VoiceUserMutedEvent;
import org.bigbluebutton.webconference.voice.events.VoiceUserTalkingEvent;
import org.bigbluebutton.webconference.voice.events.VoiceStartRecordingEvent;
import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class ESLEventListener implements IEslEventListener {
	private static Logger log = Red5LoggerFactory.getLogger(ESLEventListener.class, "bigbluebutton");
	
    private static final String START_TALKING_EVENT = "start-talking";
    private static final String STOP_TALKING_EVENT = "stop-talking";
    private static final String START_RECORDING_EVENT = "start-recording";
    private static final String STOP_RECORDING_EVENT = "stop-recording";
    private static final String VIDEO_PAUSED_EVENT = "video-paused";
    private static final String VIDEO_RESUMED_EVENT = "video-resumed";
    private static final String VIDEO_FLOOR_CHANGE_EVENT = "video-floor-change";
    
    private ConferenceEventListener conferenceEventListener;
    
    @Override
    public void conferenceEventPlayFile(String uniqueId, String confName, int confSize, EslEvent event) {
        //Ignored, Noop
    }

    @Override
    public void backgroundJobResultReceived(EslEvent event) {
        log.debug( "Background job result received [{}]", event );
    }

    @Override
    public void exceptionCaught(ExceptionEvent e) {
//        setChanged();
//        notifyObservers(e);
    }

    private static final Pattern GLOBAL_AUDION_PATTERN = Pattern.compile("(GLOBAL_AUDIO)_(.*)$");
    private static final Pattern CALLERNAME_PATTERN = Pattern.compile("(.*)-bbbID-(.*)$");
    
    @Override
    public void conferenceEventJoin(String uniqueId, String confName, int confSize, EslEvent event) {
    	
        Integer memberId = this.getMemberIdFromEvent(event);
        Map<String, String> headers = event.getEventHeaders();
        String callerId = this.getCallerIdFromEvent(event);
        String callerIdName = this.getCallerIdNameFromEvent(event);
        boolean muted = headers.get("Speak").equals("true") ? false : true; //Was inverted which was causing a State issue
        boolean speaking = headers.get("Talking").equals("true") ? true : false;

        String voiceUserId = callerIdName;
        
        log.info("User joined voice conference, user=[" + callerIdName + "], conf=[" + confName + "]");
        
        Matcher gapMatcher = GLOBAL_AUDION_PATTERN.matcher(callerIdName);
        if (gapMatcher.matches()) {
        	log.debug("Ignoring GLOBAL AUDIO USER [{}]", callerIdName);
        	return;
        }
        		
		    Matcher matcher = CALLERNAME_PATTERN.matcher(callerIdName);
		    if (matcher.matches()) {			
			    voiceUserId = matcher.group(1).trim();
			    callerIdName = matcher.group(2).trim();
		    } 
        
        VoiceUserJoinedEvent pj = new VoiceUserJoinedEvent(voiceUserId, memberId.toString(), confName, callerId, callerIdName, muted, speaking);
        conferenceEventListener.handleConferenceEvent(pj);
    }

    @Override
    public void conferenceEventLeave(String uniqueId, String confName, int confSize, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        if (memberId== null){
            return;
        }
        log.info("User left voice conference, user=[" + memberId.toString() + "], conf=[" + confName + "]");
        VoiceUserLeftEvent pl = new VoiceUserLeftEvent(memberId.toString(), confName);
        conferenceEventListener.handleConferenceEvent(pl);
    }

    @Override
    public void conferenceEventMute(String uniqueId, String confName, int confSize, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        System.out.println("******************** Received Conference Muted Event from FreeSWITCH user[" + memberId.toString() + "]");
        log.info("User muted voice conference, user=[" + memberId.toString() + "], conf=[" + confName + "]");
        VoiceUserMutedEvent pm = new VoiceUserMutedEvent(memberId.toString(), confName, true);
        conferenceEventListener.handleConferenceEvent(pm);
    }

    @Override
    public void conferenceEventUnMute(String uniqueId, String confName, int confSize, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        System.out.println("******************** Received ConferenceUnmuted Event from FreeSWITCH user[" + memberId.toString() + "]");
        log.info("User unmuted voice conference, user=[" + memberId.toString() + "], conf=[" + confName + "]");
        VoiceUserMutedEvent pm = new VoiceUserMutedEvent(memberId.toString(), confName, false);
        conferenceEventListener.handleConferenceEvent(pm);
    }

    @Override
    public void conferenceEventAction(String uniqueId, String confName, int confSize, String action, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        VoiceUserTalkingEvent pt;
        
        System.out.println("******************** Receive conference Action [" + action + "]");
        
        if (action == null) {
            return;
        }

        if (action.equals(START_TALKING_EVENT)) {
            pt = new VoiceUserTalkingEvent(memberId.toString(), confName, true);
            conferenceEventListener.handleConferenceEvent(pt);        	
        } else if (action.equals(STOP_TALKING_EVENT)) {
            pt = new VoiceUserTalkingEvent(memberId.toString(), confName, false);
            conferenceEventListener.handleConferenceEvent(pt);        	
        } else {
        	log.debug("Unknown conference Action [{}]", action);
        	System.out.println("Unknown conference Action [" + action + "]");
        }
    }

    @Override
    public void conferenceEventTransfer(String uniqueId, String confName, int confSize, EslEvent event) {
        //Ignored, Noop
    }

    @Override
    public void conferenceEventThreadRun(String uniqueId, String confName, int confSize, EslEvent event) {
    	
    }
    
    //@Override
    public void conferenceEventRecord(String uniqueId, String confName, int confSize, EslEvent event) {
    	String action = event.getEventHeaders().get("Action");
    	
        if(action == null) {          
            return;
        }
        
    	if (log.isDebugEnabled())
    		log.debug("Handling conferenceEventRecord " + action);
    	
    	if (action.equals(START_RECORDING_EVENT)) {
            VoiceStartRecordingEvent sre = new VoiceStartRecordingEvent(confName, true);
            sre.setRecordingFilename(getRecordFilenameFromEvent(event));
            sre.setTimestamp(genTimestamp().toString());
            
            log.info("Voice conference recording started. file=[" + getRecordFilenameFromEvent(event) + "], conf=[" + confName + "]");
            
            conferenceEventListener.handleConferenceEvent(sre);    		
    	} else if (action.equals(STOP_RECORDING_EVENT)) {
        	VoiceStartRecordingEvent srev = new VoiceStartRecordingEvent(confName, false);
            srev.setRecordingFilename(getRecordFilenameFromEvent(event));
            srev.setTimestamp(genTimestamp().toString());
            
            log.info("Voice conference recording stopped. file=[" + getRecordFilenameFromEvent(event) + "], conf=[" + confName + "]");           
            conferenceEventListener.handleConferenceEvent(srev);    		
    	} else {
        	if (log.isDebugEnabled())
        		log.warn("Processing UNKNOWN conference Action {}", action);
    	}
    }

    private Long genTimestamp() {
    	return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
    
    @Override
    public void eventReceived(EslEvent event) {
        log.debug("ESL Event Listener received event=[" + event.getEventName() + "]");

        String action = event.getEventHeaders().get("Action");
        String confName = event.getEventHeaders().get("Conference-Name");
        if (action != null && confName != null) {
            switch (action) {
                case VIDEO_PAUSED_EVENT:
                    VideoPausedEvent vPaused = new VideoPausedEvent(confName);
                    conferenceEventListener.handleConferenceEvent(vPaused);
                    break;
                case VIDEO_RESUMED_EVENT:
                    VideoResumedEvent vResumed = new VideoResumedEvent(confName);
                    conferenceEventListener.handleConferenceEvent(vResumed);
                    break;
                case VIDEO_FLOOR_CHANGE_EVENT:
                    String holderMemberId = getNewFloorHolderMemberIdFromEvent(event);
                    VideoFloorChangedEvent vFloor= new VideoFloorChangedEvent(confName, holderMemberId);
                    conferenceEventListener.handleConferenceEvent(vFloor);
                    break;

                default:
                    log.debug("Unknown conference Action [{}]", action);
            }
        }
	}

    private Integer getMemberIdFromEvent(EslEvent e) {        
        try{
            return new Integer(e.getEventHeaders().get("Member-ID"));
        }catch (NumberFormatException excp){
            return null;
        }
    }

    private String getCallerIdFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Caller-Caller-ID-Number");
    }

    private String getCallerIdNameFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Caller-Caller-ID-Name");
    }
    
    private String getRecordFilenameFromEvent(EslEvent e) {
    	return e.getEventHeaders().get("Path");
    }
    
    private String getRecordTimestampFromEvent(EslEvent e) {
    	return e.getEventHeaders().get("Event-Date-Timestamp");
    }
	
    public void setConferenceEventListener(ConferenceEventListener listener) {
        this.conferenceEventListener = listener;
    }

    private String getNewFloorHolderMemberIdFromEvent(EslEvent e) {
        String newHolder = e.getEventHeaders().get("New-ID");
        if(newHolder == null || newHolder.equalsIgnoreCase("none")) {
            newHolder = "";
        }
        return newHolder;
    }
}
