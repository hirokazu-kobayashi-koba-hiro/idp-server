package org.idp.sample.domain.model.notification;

public interface EmailSendingGateway {

  void send(EmailSendingRequest request);
}
