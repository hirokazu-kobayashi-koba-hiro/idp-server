package org.idp.server.adapters.springboot.authentication;

import java.util.*;
import org.idp.server.adapters.springboot.authorization.OAuthSessionService;
import org.idp.server.adapters.springboot.notification.NotificationService;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.mfa.*;
import org.idp.server.core.mfa.email.*;
import org.idp.server.core.notification.EmailSendingRequest;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailAuthenticationService implements MfaInteractor {

  NotificationService notificationService;
  OAuthSessionService oAuthSessionService;
  String sender;

  public EmailAuthenticationService(
      NotificationService notificationService,
      OAuthSessionService oAuthSessionService,
      @Value("${idp.configurations.email.sender}") String sender) {
    this.notificationService = notificationService;
    this.oAuthSessionService = oAuthSessionService;
    this.sender = sender;
  }

  @Override
  public MfaInteractionResult interact(
      Tenant tenant,
      OAuthSession oAuthSession,
      MfaInteractionType type,
      Map<String, Object> params,
      UserRepository userRepository) {
    switch (type.name()) {
      case "EMAIL_VERIFICATION_CHALLENGE" -> {
        EmailVerificationChallenge emailVerificationChallenge = challenge(oAuthSession.user());

        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("emailVerificationChallenge", emailVerificationChallenge);
        OAuthSession updatedSession = oAuthSession.addAttribute(attributes);

        oAuthSessionService.updateSession(updatedSession);

        return new MfaInteractionResult(
            type, Map.of(), DefaultSecurityEventType.email_verification_request);
      }

      case "EMAIL_VERIFICATION" -> {
        String verificationCode = (String) params.getOrDefault("verification_code", "");

        if (!oAuthSession.hasAttribute("emailVerificationChallenge")) {
          throw new EmailVerificationChallengeNotFoundException(
              "emailVerificationChallenge is not found");
        }
        EmailVerificationChallenge emailVerificationChallenge =
            (EmailVerificationChallenge) oAuthSession.getAttribute("emailVerificationChallenge");

        verify(verificationCode, emailVerificationChallenge);

        User user = oAuthSession.user();

        Authentication authentication =
            new Authentication()
                .setTime(SystemDateTime.now())
                .addMethods(new ArrayList<>(List.of("otp")))
                .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

        Map<String, Object> response = new HashMap<>();
        response.put("user", user.toMap());
        response.put("authentication", authentication.toMap());

        return new MfaInteractionResult(
            type, user, authentication, response, DefaultSecurityEventType.email_verification_success);
      }
    }

    throw new MfaInteractorUnSupportedException(
        String.format("Email verification not supported: %s", type));
  }

  private EmailVerificationChallenge challenge(User user) {
    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    String to = user.email();
    String subject = "idp-server email verification";
    String body = String.format("email verification code: %s", oneTimePassword.value());

    EmailSendingRequest emailSendingRequest = new EmailSendingRequest(sender, to, subject, body);
    notificationService.sendEmail(emailSendingRequest);

    return new EmailVerificationChallenge(
        new EmailVerificationIdentifier(UUID.randomUUID().toString()), oneTimePassword);
  }

  private void verify(String input, EmailVerificationChallenge challenge) {

    EmailVerificationCodeVerifier emailVerificationCodeVerifier =
        new EmailVerificationCodeVerifier(input, challenge);
    emailVerificationCodeVerifier.verify();
  }
}
