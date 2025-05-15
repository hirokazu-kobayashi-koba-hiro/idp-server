package org.idp.server.core.authentication.email;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.notification.EmailSender;
import org.idp.server.core.authentication.notification.EmailSenders;
import org.idp.server.core.authentication.notification.EmailSendingRequest;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.security.event.DefaultSecurityEventType;

public class EmailAuthenticationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionCommandRepository transactionCommandRepository;
  EmailSenders emailSenders;

  public EmailAuthenticationChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionCommandRepository transactionCommandRepository,
      EmailSenders emailSenders) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.transactionCommandRepository = transactionCommandRepository;
    this.emailSenders = emailSenders;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository) {

    EmailAuthenticationConfiguration emailAuthenticationConfiguration =
        configurationQueryRepository.get(tenant, "email", EmailAuthenticationConfiguration.class);

    String email = request.optValueAsString("email", "");

    if (email.isEmpty()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "email is required.");

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          response,
          DefaultSecurityEventType.email_verification_failure);
    }

    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    String sender = emailAuthenticationConfiguration.sender();
    EmailVerificationTemplate emailVerificationTemplate =
        emailAuthenticationConfiguration.findTemplate(request.optValueAsString("email_template", "authentication"));
    String subject = emailVerificationTemplate.subject();
    int retryCountLimitation = emailAuthenticationConfiguration.retryCountLimitation();
    int expireSeconds = emailAuthenticationConfiguration.expireSeconds();

    String body = emailVerificationTemplate.interpolateBody(oneTimePassword.value(), expireSeconds);

    EmailSendingRequest emailSendingRequest = new EmailSendingRequest(sender, email, subject, body);

    EmailSender emailSender = emailSenders.get(emailAuthenticationConfiguration.senderType());
    emailSender.send(emailSendingRequest, emailAuthenticationConfiguration.setting());

    EmailVerificationChallenge emailVerificationChallenge =
        EmailVerificationChallenge.create(oneTimePassword, retryCountLimitation, expireSeconds);

    transactionCommandRepository.register(
        tenant, authorizationIdentifier, "email", emailVerificationChallenge);

    User user = userQueryRepository.findByEmail(tenant, email, "idp-server");

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        new Authentication(),
        Map.of(),
        DefaultSecurityEventType.email_verification_request);
  }
}
