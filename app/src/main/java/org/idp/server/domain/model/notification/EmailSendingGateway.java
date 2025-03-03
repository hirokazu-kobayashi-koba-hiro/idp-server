package org.idp.server.domain.model.notification;

public interface EmailSendingGateway {

  void send(EmailSendingRequest request);
}
