package org.idp.server.adapters.springboot.mfa.email;

import java.util.Map;
import org.idp.server.core.mfa.*;
import org.idp.server.core.mfa.email.*;
import org.idp.server.core.notification.EmailSender;
import org.idp.server.core.notification.EmailSendingRequest;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class EmailAuthenticationChallengeInteractor implements MfaInteractor {

  MfaConfigurationQueryRepository configurationQueryRepository;
  MfaTransactionCommandRepository transactionCommandRepository;
  EmailSender emailSender;

  public EmailAuthenticationChallengeInteractor(
      MfaConfigurationQueryRepository configurationQueryRepository,
      MfaTransactionCommandRepository transactionCommandRepository,
      EmailSender emailSender) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.transactionCommandRepository = transactionCommandRepository;
    this.emailSender = emailSender;
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
      return new MfaInteractionResult(
          MfaInteractionStatus.CLIENT_ERROR,
          type,
          Map.of(),
          DefaultSecurityEventType.email_verification_failure);
    }

    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    String sender = emailMfaConfiguration.sender();
    String to = oAuthSession.user().email();
    String subject = emailMfaConfiguration.subject();

    String body = emailMfaConfiguration.interpolateBody(oneTimePassword.value());

    EmailSendingRequest emailSendingRequest = new EmailSendingRequest(sender, to, subject, body);
    emailSender.send(emailSendingRequest);

    EmailVerificationChallenge emailVerificationChallenge =
        new EmailVerificationChallenge(oneTimePassword.value());

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
