package org.idp.server.core.notification;

public interface EmailSender {

  void send(EmailSendingRequest request);
}
