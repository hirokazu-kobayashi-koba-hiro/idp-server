package org.idp.server.authentication.interactors.notification;


import org.idp.server.authentication.interactors.email.EmailSenderSetting;

public interface EmailSender {

  EmailSenderType type();

  void send(EmailSendingRequest request, EmailSenderSetting setting);
}
