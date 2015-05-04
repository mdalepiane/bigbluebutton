package org.bigbluebutton.core.apps.voice

import org.bigbluebutton.core.api._
import org.bigbluebutton.core.MeetingActor

trait VoiceApp {
  this : MeetingActor =>
  
  val outGW: MessageOutGateway

  def handleSipVideoPaused(msg: SipVideoPaused) {
    isSipVideoPresent = false
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, activeTalker))
  }

  def handleSipVideoResumed(msg: SipVideoResumed) {
    isSipVideoPresent = true
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, activeTalker))
  }

  def handleActiveTalkerChanged(msg: ActiveTalkerChanged) {
    activeTalker = msg.activeTalker
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, activeTalker))
  }

  def handleGlobalVideoStreamInfo(msg: GlobalVideoStreamInfo) {
    globalVideoStreamName = msg.globalVideoStreamName
    outGW.send(new GlobalVideoStreamInfoMessage(msg.meetingID, msg.globalVideoStreamName))
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, activeTalker)) //update video everytime it informs about globalStream
  }
}
