package org.idp.server.platform.notification;


public interface EmailSender {

  EmailSenderType type();

  void send(EmailSendingRequest request, EmailSenderSetting setting);
}
