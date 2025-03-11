package org.idp.server.adapters.springboot.infrastructure.client.email;

import org.idp.server.adapters.springboot.domain.model.notification.EmailSendingGateway;
import org.idp.server.adapters.springboot.domain.model.notification.EmailSendingRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class GmailSendingClient implements EmailSendingGateway {

  JavaMailSender mailSender;

  public GmailSendingClient(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Override
  public void send(EmailSendingRequest request) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(request.to());
    message.setSubject(request.subject());
    message.setText(request.body());
    message.setFrom(request.from());
    mailSender.send(message);
  }
}
