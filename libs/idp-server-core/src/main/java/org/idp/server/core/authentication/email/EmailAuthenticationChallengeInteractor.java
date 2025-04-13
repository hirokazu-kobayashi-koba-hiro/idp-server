package org.idp.server.core.authentication.email;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.notification.EmailSender;
import org.idp.server.core.notification.EmailSenders;
import org.idp.server.core.notification.EmailSendingRequest;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class EmailAuthenticationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationTransactionCommandRepository transactionCommandRepository;
  EmailSenders emailSenders;

  public EmailAuthenticationChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationTransactionCommandRepository transactionCommandRepository,
      EmailSenders emailSenders) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.transactionCommandRepository = transactionCommandRepository;
    this.emailSenders = emailSenders;
  }

  @Override
  public AuthenticationInteractionResult interact(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      OAuthSession oAuthSession,
      UserRepository userRepository) {

    EmailAuthenticationConfiguration emailAuthenticationConfiguration =
        configurationQueryRepository.get(tenant, "email", EmailAuthenticationConfiguration.class);

    if (!oAuthSession.hasUser()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "session is invalid. email is not specified");

      return new AuthenticationInteractionResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          response,
          DefaultSecurityEventType.email_verification_failure);
    }

    if (!request.containsKey("email_template")) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "session is invalid. email is not specified");

      return new AuthenticationInteractionResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          response,
          DefaultSecurityEventType.email_verification_failure);
    }

    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    String sender = emailAuthenticationConfiguration.sender();
    String to = oAuthSession.user().email();
    EmailTemplate emailTemplate =
        emailAuthenticationConfiguration.findTemplate(request.getValueAsString("email_template"));
    String subject = emailTemplate.subject();
    int retryCountLimitation = emailAuthenticationConfiguration.retryCountLimitation();
    int expireSeconds = emailAuthenticationConfiguration.expireSeconds();

    String body = emailTemplate.interpolateBody(oneTimePassword.value(), expireSeconds);

    EmailSendingRequest emailSendingRequest = new EmailSendingRequest(sender, to, subject, body);

    EmailSender emailSender = emailSenders.get(emailAuthenticationConfiguration.senderType());
    emailSender.send(emailSendingRequest, emailAuthenticationConfiguration.setting());

    EmailVerificationChallenge emailVerificationChallenge =
        EmailVerificationChallenge.create(oneTimePassword, retryCountLimitation, expireSeconds);

    transactionCommandRepository.register(
        tenant, authenticationTransactionIdentifier, "email", emailVerificationChallenge);

    return new AuthenticationInteractionResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        oAuthSession.user(),
        new Authentication(),
        Map.of(),
        DefaultSecurityEventType.email_verification_request);
  }
}
