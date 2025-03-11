package org.idp.server.adapters.springboot.application.service.authentication;

import java.util.UUID;

import org.idp.server.adapters.springboot.domain.model.authentication.*;
import org.idp.server.adapters.springboot.application.service.notification.NotificationService;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.adapters.springboot.domain.model.notification.EmailSendingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailAuthenticationService {

  NotificationService notificationService;
  String sender;

  public EmailAuthenticationService(
      NotificationService notificationService,
      @Value("${idp.configurations.email.sender}") String sender) {
    this.notificationService = notificationService;
    this.sender = sender;
  }

  public EmailVerificationChallenge challenge(User user) {
    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    String to = user.email();
    String subject = "idp-server email verification";
    String body = String.format("email verification code: %s", oneTimePassword.value());

    EmailSendingRequest emailSendingRequest = new EmailSendingRequest(sender, to, subject, body);
    notificationService.sendEmail(emailSendingRequest);

    return new EmailVerificationChallenge(
        new EmailVerificationIdentifier(UUID.randomUUID().toString()), oneTimePassword);
  }

  public void verify(String input, EmailVerificationChallenge challenge) {

    EmailVerificationCodeVerifier emailVerificationCodeVerifier =
        new EmailVerificationCodeVerifier(input, challenge);
    emailVerificationCodeVerifier.verify();
  }
}
