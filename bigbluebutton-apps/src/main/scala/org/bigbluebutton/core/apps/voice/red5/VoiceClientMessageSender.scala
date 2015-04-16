package org.bigbluebutton.core.apps.voice.red5

import com.google.gson.Gson
import org.bigbluebutton.conference.meeting.messaging.red5.ConnectionInvokerService
import org.bigbluebutton.conference.meeting.messaging.red5.BroadcastClientMessage
import org.bigbluebutton.core.api._

class VoiceClientMessageSender(service: ConnectionInvokerService) extends OutMessageListener2 {

  def handleMessage(msg: IOutMessage) {
    msg match {
      case msg: SipVideoUpdated               => handleSipVideoUpdated(msg)
      case _ => // do nothing
    }
  }

  private def handleSipVideoUpdated(msg: SipVideoUpdated) {
    val args = new java.util.HashMap[String, Object]()
    args.put(Constants.VOICE_CONF, msg.voiceBridge)
    args.put(Constants.SIP_VIDEO_PRESENT, msg.sipVideoPresent:java.lang.Boolean)
    args.put(Constants.ACTIVE_TALKER, msg.activeTalker)

    val message = new java.util.HashMap[String, Object]()
    val gson = new Gson()
    message.put("msg", gson.toJson(args))

    var m = new BroadcastClientMessage(msg.meetingID, MessageNames.SIP_VIDEO_UPDATE, message)
    service.sendMessage(m)
  }
}
