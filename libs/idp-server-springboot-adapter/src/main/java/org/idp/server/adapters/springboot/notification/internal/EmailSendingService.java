package org.idp.server.adapters.springboot.notification.internal;

import org.idp.server.core.notification.EmailSendingGateway;
import org.idp.server.core.notification.EmailSendingRequest;
import org.springframework.stereotype.Service;

@Service
public class EmailSendingService {

  EmailSendingGateway emailSendingGateway;

  public EmailSendingService(EmailSendingGateway emailSendingGateway) {
    this.emailSendingGateway = emailSendingGateway;
  }

  public void send(EmailSendingRequest request) {
    emailSendingGateway.send(request);
  }
}
