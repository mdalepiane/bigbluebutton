package org.bigbluebutton.webconference.voice.events;

public class VideoFloorChangedEvent extends VoiceConferenceEvent {

    /**
     * Voice user id of the new floor holder
     */
    private final String voiceUserId;

    public VideoFloorChangedEvent(String room, String voiceUserId) {
        super(room);
        this.voiceUserId = voiceUserId;
    }

    public String getFloorHolderVoiceUserId() {
        return this.voiceUserId;
    }
}
