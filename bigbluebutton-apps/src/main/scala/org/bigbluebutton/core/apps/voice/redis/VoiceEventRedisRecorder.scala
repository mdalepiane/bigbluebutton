package org.bigbluebutton.core.apps.voice.redis

import org.bigbluebutton.core.api._
import org.bigbluebutton.conference.service.recorder.RecorderApplication
import org.bigbluebutton.conference.service.recorder.voice.SipVideoUpdatedRecordEvent

class VoiceEventRedisRecorder(recorder: RecorderApplication) extends OutMessageListener2 {

  def handleMessage(msg: IOutMessage) {
    msg match {
      case msg: SipVideoUpdated               => handleSipVideoUpdated(msg)
      case _ => // Do nothing
    }
  }

  private def handleSipVideoUpdated(msg: SipVideoUpdated) {
    if (msg.recorded) {
      val ev = new SipVideoUpdatedRecordEvent();
      ev.setTimestamp(TimestampGenerator.generateTimestamp);
      ev.setMeetingId(msg.meetingID);
      ev.setSipVideoPresent(msg.sipVideoPresent.toString());
      ev.setActiveTalker(msg.activeTalker);
      recorder.record(msg.meetingID, ev);
    }
  }
}
