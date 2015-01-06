package org.bigbluebutton.core.apps.video

import org.bigbluebutton.core.api._
import org.bigbluebutton.core.MeetingActor

trait VideoApp {
	this : MeetingActor =>

	val outGW: MessageOutGateway

	def handleGetStreamPath(msg: GetStreamPath) {
		outGW.send(new GetStreamPathRequest(msg.meetingID, msg.requesterID, msg.clientAddr, msg.streamName))
		val streamPath = msg.defaultPath
		// TODO: Move the reply to a handler
		outGW.send(new GetStreamPathReply(msg.meetingID, msg.requesterID, msg.streamName, streamPath))
	}
}
