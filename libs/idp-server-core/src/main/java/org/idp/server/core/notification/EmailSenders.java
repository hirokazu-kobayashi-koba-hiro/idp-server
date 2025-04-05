package org.idp.server.core.notification;

import java.util.Map;
import org.idp.server.core.type.exception.UnSupportedException;

public class EmailSenders {

  Map<EmailSenderType, EmailSender> senders;

  public EmailSenders(Map<EmailSenderType, EmailSender> senders) {
    this.senders = senders;
  }

  public EmailSender get(EmailSenderType type) {
    EmailSender emailSender = senders.get(type);

    if (emailSender == null) {
      throw new UnSupportedException("No EmailSender found for type " + type);
    }

    return emailSender;
  }
}
