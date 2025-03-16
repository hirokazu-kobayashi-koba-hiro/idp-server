package org.idp.server.core.notification;

public interface EmailSendingGateway {

  void send(EmailSendingRequest request);
}
