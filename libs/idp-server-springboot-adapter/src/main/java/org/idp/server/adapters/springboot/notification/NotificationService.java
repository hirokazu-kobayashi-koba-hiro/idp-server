package org.idp.server.adapters.springboot.notification;

import org.idp.server.core.notification.EmailSender;
import org.idp.server.core.notification.EmailSendingRequest;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  EmailSender emailSender;

  public NotificationService(EmailSender emailSender) {
    this.emailSender = emailSender;
  }

  public void sendEmail(EmailSendingRequest request) {
    emailSender.send(request);
  }
}
