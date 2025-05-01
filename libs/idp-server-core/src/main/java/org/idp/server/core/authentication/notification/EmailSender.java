package org.idp.server.core.authentication.notification;

import org.idp.server.core.authentication.email.EmailSenderSetting;

public interface EmailSender {

  EmailSenderType type();

  void send(EmailSendingRequest request, EmailSenderSetting setting);
}
