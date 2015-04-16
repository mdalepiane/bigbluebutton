package org.bigbluebutton.core.apps.voice.redis

import org.bigbluebutton.core.api._
import org.bigbluebutton.conference.service.messaging.MessagingConstants
import org.bigbluebutton.core.messaging.Util
import com.google.gson.Gson
import org.bigbluebutton.core.api.UserVO
import collection.JavaConverters._
import scala.collection.JavaConversions._

object VoiceMessageToJsonConverter {
  def sipVideoUpdatedToJson(msg: SipVideoUpdated):String = {
    val payload = new java.util.HashMap[String, Any]()
    payload.put(Constants.MEETING_ID, msg.meetingID)
    payload.put(Constants.VOICE_CONF, msg.voiceBridge)
    payload.put(Constants.SIP_VIDEO_PRESENT, msg.sipVideoPresent)
    payload.put(Constants.ACTIVE_TALKER, msg.activeTalker)

    val header = Util.buildHeader(MessageNames.SIP_VIDEO_UPDATE, msg.version, None)
    Util.buildJson(header, payload)
  }
}
