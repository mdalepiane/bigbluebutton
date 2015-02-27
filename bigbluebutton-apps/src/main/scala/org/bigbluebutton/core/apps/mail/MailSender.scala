package org.bigbluebutton.core.apps.mail

import org.bigbluebutton.core.api._
import org.bigbluebutton.conference.service.mail.MailApplication
import org.bigbluebutton.core.api.OutMessageListener2
import org.bigbluebutton.core.api.IOutMessage

class MailSender(mailSender: MailApplication) extends OutMessageListener2 {

  def handleMessage(msg: IOutMessage) {
    msg match {
      case msg: MailSharedNoteOutMsg      => handleMailSharedNoteOutMsg(msg)
      case _ => // do nothing
    }
  }

  private def handleMailSharedNoteOutMsg(msg: MailSharedNoteOutMsg) {
    mailSender.sendMail(msg.email, msg.message);
  }
}
