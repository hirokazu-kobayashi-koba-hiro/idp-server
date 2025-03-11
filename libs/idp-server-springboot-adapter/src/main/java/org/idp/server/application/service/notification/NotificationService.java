package org.idp.server.application.service.notification;

import org.idp.server.application.service.notification.internal.EmailSendingService;
import org.idp.server.domain.model.notification.EmailSendingRequest;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  EmailSendingService emailSendingService;

  public NotificationService(EmailSendingService emailSendingService) {
    this.emailSendingService = emailSendingService;
  }

  public void sendEmail(EmailSendingRequest request) {
    emailSendingService.send(request);
  }
}
