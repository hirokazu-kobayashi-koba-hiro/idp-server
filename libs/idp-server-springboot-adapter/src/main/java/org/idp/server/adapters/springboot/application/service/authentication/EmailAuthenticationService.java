package org.idp.server.adapters.springboot.application.service.authentication;

import java.util.*;

import org.idp.server.adapters.springboot.application.service.authorization.OAuthSessionService;
import org.idp.server.adapters.springboot.domain.model.authentication.*;
import org.idp.server.adapters.springboot.application.service.notification.NotificationService;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.adapters.springboot.domain.model.notification.EmailSendingRequest;
import org.idp.server.core.oauth.interaction.OAuthInteractorUnSupportedException;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionResult;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.oauth.interaction.OAuthUserInteractor;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.sharedsignal.DefaultEventType;
import org.idp.server.core.tenant.Tenant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailAuthenticationService implements OAuthUserInteractor {

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
  public OAuthUserInteractionResult interact(Tenant tenant, AuthorizationRequest authorizationRequest, OAuthUserInteractionType type, Map<String, Object> params) {
    switch (type) {
      case EMAIL_VERIFICATION_CHALLENGE -> {
        OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

        EmailVerificationChallenge emailVerificationChallenge =
                challenge(oAuthSession.user());

        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("emailVerificationChallenge", emailVerificationChallenge);
        OAuthSession updatedSession = oAuthSession.addAttribute(attributes);

        oAuthSessionService.updateSession(updatedSession);
      }

      case EMAIL_VERIFICATION -> {
        String verificationCode = (String) params.getOrDefault("verification_code", "");

        OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());
        if (!oAuthSession.hasAttribute("emailVerificationChallenge")) {
          throw new EmailVerificationChallengeNotFoundException(
                  "emailVerificationChallenge is not found");
        }
        EmailVerificationChallenge emailVerificationChallenge =
                (EmailVerificationChallenge) oAuthSession.getAttribute("emailVerificationChallenge");

        verify(verificationCode, emailVerificationChallenge);

        User user = oAuthSession.user();
        User maskedUser = user.didEmailVerification().maskPassword();

        Authentication authentication =
                new Authentication()
                        .setTime(SystemDateTime.now())
                        .addMethods(new ArrayList<>(List.of("otp")))
                        .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

        Map<String, Object> response = new HashMap<>();
        response.put("user", maskedUser);

        return new OAuthUserInteractionResult(type, user, authentication, response, DefaultEventType.email_verification_success);
      }
    }

    throw new OAuthInteractorUnSupportedException(String.format("Email verification not supported: %s", type));
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
