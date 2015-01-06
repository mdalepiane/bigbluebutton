package org.bigbluebutton.core.apps.video.redis

import org.bigbluebutton.core.api._
import org.bigbluebutton.conference.service.messaging.redis.MessageSender
import org.bigbluebutton.conference.service.messaging.MessagingConstants

class VideoEventRedisPublisher(service: MessageSender) extends OutMessageListener2 {

  def handleMessage(msg: IOutMessage) {
    msg match {
      case msg: GetStreamPathRequest      => handleGetStreamPathRequest(msg)
      case _ => // do nothing
    }
  }

  private def handleGetStreamPathRequest(msg: GetStreamPathRequest) {
    val json = VideoMessageToJsonConverter.getStreamPathRequestToJson(msg)
    service.send(MessagingConstants.FROM_VIDEO_CHANNEL, json)
  }
}
