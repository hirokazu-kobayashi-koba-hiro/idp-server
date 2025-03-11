package org.idp.server.domain.model.notification;

import org.idp.server.domain.model.notification.EmailSendingRequest;

public interface EmailSendingGateway {

  void send(EmailSendingRequest request);
}
