package org.bigbluebutton.core.apps.video.redis

import org.bigbluebutton.core.api._
import org.bigbluebutton.core.messaging.Util
import com.google.gson.Gson

object VideoMessageToJsonConverter {
  def getStreamPathRequestToJson(msg: GetStreamPathRequest):String = {
    val payload = new java.util.HashMap[String, Any]()
    payload.put(Constants.MEETING_ID, msg.meetingID)
    payload.put(Constants.EXTERNAL_MEETING_ID, msg.externalMeetingID)
    payload.put(Constants.REQUESTER_ID, msg.requesterID)
    payload.put(Constants.CLIENT_ADDR, msg.clientAddr)
    payload.put(Constants.STREAM, msg.streamName)

    val header = Util.buildHeader(MessageNames.GET_STREAM_PATH, msg.version, None)
    Util.buildJson(header, payload)
  }
}
