package org.idp.server.adapters.springboot.domain.model.notification;

public interface EmailSendingGateway {

  void send(EmailSendingRequest request);
}
