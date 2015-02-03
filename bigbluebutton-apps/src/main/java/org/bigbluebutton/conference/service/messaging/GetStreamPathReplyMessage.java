package org.bigbluebutton.conference.service.messaging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class GetStreamPathReplyMessage implements IMessage {

	private static Logger log = Red5LoggerFactory.getLogger(GetStreamPathReplyMessage.class, "bigbluebutton");

	public static final String GET_STREAM_PATH_REPLY  = MessagingConstants.GET_STREAM_PATH_REPLY;
	public static final String VERSION = "0.0.1";

	public final String meetingID;
	public final String requesterID;
	public final String streamName;
	public final String streamPath;

	public GetStreamPathReplyMessage(String meetingID, String requesterID, String streamName, String streamPath) {
		this.meetingID = meetingID;
		this.requesterID = requesterID;
		this.streamName = streamName;
		this.streamPath = streamPath;
	}

	public static GetStreamPathReplyMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (GET_STREAM_PATH_REPLY.equals(messageName)) {
					if (payload.has(Constants.MEETING_ID)
							&& payload.has(Constants.REQUESTER_ID)
							&& payload.has(Constants.STREAM)
							&& payload.has(Constants.STREAM_PATH)) {
						String meetingID = payload.get(Constants.MEETING_ID).getAsString();
						String requesterID = payload.get(Constants.REQUESTER_ID).getAsString();
						String streamName = payload.get(Constants.STREAM).getAsString();
						// Convert list of server into a stream path as expected by the client.
						// server_ip1/server_ip2/.../server_ipN
						JsonArray path = payload.get(Constants.STREAM_PATH).getAsJsonArray();
						String streamPath = "";
						for(JsonElement e : path) {
							streamPath += e.getAsString() + "/";
						}
						// Remove trailing slash
						streamPath = streamPath.replaceAll("/$", "");
						return new GetStreamPathReplyMessage(meetingID, requesterID, streamName, streamPath);
					}
				}
			}
		}
		return null;
	}

}
