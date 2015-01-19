package org.bigbluebutton.core.apps.video

import org.bigbluebutton.core.api._
import org.bigbluebutton.core.MeetingActor

trait VideoApp {
	this : MeetingActor =>

	val outGW: MessageOutGateway
	var defaultStreamPath: String = ""

	def handleGetStreamPath(msg: GetStreamPath) {
		outGW.send(new GetStreamPathRequest(msg.meetingID, msg.requesterID, msg.clientAddr, msg.streamName))
		defaultStreamPath = msg.defaultPath
	}

	def handleGetStreamPathReply(msg: GetStreamPathReplyInMsg) {
		outGW.send(new GetStreamPathReplyOutMsg(msg.meetingID, msg.requesterID, msg.streamName, msg.streamPath))
	}
}
