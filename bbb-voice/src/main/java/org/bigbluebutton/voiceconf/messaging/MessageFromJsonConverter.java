package org.bigbluebutton.voiceconf.messaging;

import org.bigbluebutton.voiceconf.messaging.messages.IMessage;
import org.bigbluebutton.voiceconf.messaging.messages.UpdateVideoStatus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MessageFromJsonConverter {

	public static IMessage convert(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				switch (messageName) {
				  case UpdateVideoStatus.UPDATE_VIDEO_STATUS_REQUEST_EVENT:
					  return processUpdateVideoStatus(payload);
				}
			}
		}
		return null;
	}

	private static IMessage processUpdateVideoStatus(JsonObject payload) {
		String voiceBridge = payload.get(Constants.VOICE_CONF).getAsString();
		String floorHolder = payload.get(Constants.ACTIVE_TALKER).getAsString();
		Boolean videoPresent = payload.get(Constants.SIP_VIDEO_PRESENT).getAsBoolean();
		return new UpdateVideoStatus(voiceBridge, floorHolder, videoPresent);
	}
}
