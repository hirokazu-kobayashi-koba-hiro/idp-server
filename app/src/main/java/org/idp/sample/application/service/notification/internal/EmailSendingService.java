package org.idp.sample.application.service.notification.internal;

import org.idp.sample.domain.model.notification.EmailSendingGateway;
import org.idp.sample.domain.model.notification.EmailSendingRequest;
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
