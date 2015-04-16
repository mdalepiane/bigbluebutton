package org.bigbluebutton.voiceconf.messaging;

import org.bigbluebutton.voiceconf.messaging.messages.IMessage;
import org.bigbluebutton.voiceconf.messaging.messages.UpdateVideoStatus;
import org.bigbluebutton.voiceconf.red5.Service;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class VoiceMessageHandler implements MessageHandler {
    private static Logger log = Red5LoggerFactory.getLogger(VoiceMessageHandler.class, "sip");

    private Service service;

    public void handleMessage(String pattern, String channel, String message) {
        if (channel.equalsIgnoreCase(MessagingConstants.TO_BBB_VOICE_CHANNEL)) {
            IMessage msg = MessageFromJsonConverter.convert(message);

            if (msg != null) {
                if(msg instanceof UpdateVideoStatus) {
                    UpdateVideoStatus uvs = (UpdateVideoStatus) msg;
                    log.info("Handling Update Video Status [{}, {}, {}]", uvs.voiceBridge, uvs.floorHolder, uvs.videoPresent);
                    service.updateVideoStatus(uvs.voiceBridge, uvs.floorHolder, uvs.videoPresent);
                }
            }
        }
    }

    public void setVoiceService(Service voiceService){
        this.service = voiceService;
    }
}
