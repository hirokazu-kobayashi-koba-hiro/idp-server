package org.idp.server.adapters.springboot.notification;

import org.idp.server.adapters.springboot.notification.internal.EmailSendingService;
import org.idp.server.core.notification.EmailSendingRequest;
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
