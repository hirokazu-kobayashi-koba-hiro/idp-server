package org.idp.server.core.mfa.email;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.mfa.*;
import org.idp.server.core.notification.EmailSender;
import org.idp.server.core.notification.EmailSenders;
import org.idp.server.core.notification.EmailSendingRequest;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class EmailAuthenticationChallengeInteractor implements MfaInteractor {

  MfaConfigurationQueryRepository configurationQueryRepository;
  MfaTransactionCommandRepository transactionCommandRepository;
  EmailSenders emailSenders;

  public EmailAuthenticationChallengeInteractor(
      MfaConfigurationQueryRepository configurationQueryRepository,
      MfaTransactionCommandRepository transactionCommandRepository,
      EmailSenders emailSenders) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.transactionCommandRepository = transactionCommandRepository;
    this.emailSenders = emailSenders;
  }

  @Override
  public MfaInteractionResult interact(
      Tenant tenant,
      MfaTransactionIdentifier mfaTransactionIdentifier,
      MfaInteractionType type,
      MfaInteractionRequest request,
      OAuthSession oAuthSession,
      UserRepository userRepository) {

    EmailMfaConfiguration emailMfaConfiguration =
        configurationQueryRepository.get(tenant, "email", EmailMfaConfiguration.class);

    if (!oAuthSession.hasUser()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "session is invalid. email is not specified");

      return new MfaInteractionResult(
          MfaInteractionStatus.CLIENT_ERROR,
          type,
          response,
          DefaultSecurityEventType.email_verification_failure);
    }

    if (!request.containsKey("email_template")) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "session is invalid. email is not specified");

      return new MfaInteractionResult(
          MfaInteractionStatus.CLIENT_ERROR,
          type,
          response,
          DefaultSecurityEventType.email_verification_failure);
    }

    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    String sender = emailMfaConfiguration.sender();
    String to = oAuthSession.user().email();
    EmailTemplate emailTemplate =
        emailMfaConfiguration.findTemplate(request.getValueAsString("email_template"));
    String subject = emailTemplate.subject();
    int retryCountLimitation = emailMfaConfiguration.retryCountLimitation();
    int expireSeconds = emailMfaConfiguration.expireSeconds();

    String body = emailTemplate.interpolateBody(oneTimePassword.value(), expireSeconds);

    EmailSendingRequest emailSendingRequest = new EmailSendingRequest(sender, to, subject, body);

    EmailSender emailSender = emailSenders.get(emailMfaConfiguration.senderType());
    emailSender.send(emailSendingRequest, emailMfaConfiguration.setting());

    EmailVerificationChallenge emailVerificationChallenge =
        EmailVerificationChallenge.create(oneTimePassword, retryCountLimitation, expireSeconds);

    transactionCommandRepository.register(
        mfaTransactionIdentifier, "email", emailVerificationChallenge);

    return new MfaInteractionResult(
        MfaInteractionStatus.SUCCESS,
        type,
        oAuthSession.user(),
        new Authentication(),
        Map.of(),
        DefaultSecurityEventType.email_verification_request);
  }
}
