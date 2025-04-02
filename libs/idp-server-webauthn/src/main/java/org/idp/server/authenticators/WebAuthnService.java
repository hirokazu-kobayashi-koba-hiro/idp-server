package org.idp.server.authenticators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.authenticators.webauthn.*;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnConfigurationService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnCredentialService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnSessionService;
import org.idp.server.core.authentication.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.sharedsignal.DefaultEventType;
import org.idp.server.core.tenant.Tenant;

public class WebAuthnService implements MfaInteractor {

  WebAuthnConfigurationService webAuthnConfigurationService;
  WebAuthnSessionService webAuthnSessionService;
  WebAuthnCredentialService webAuthnCredentialService;

  public WebAuthnService(
      WebAuthnConfigurationService webAuthnConfigurationService,
      WebAuthnSessionService webAuthnSessionService,
      WebAuthnCredentialService webAuthnCredentialService) {
    this.webAuthnConfigurationService = webAuthnConfigurationService;
    this.webAuthnSessionService = webAuthnSessionService;
    this.webAuthnCredentialService = webAuthnCredentialService;
  }

  @Override
  public MfaInteractionResult interact(
      Tenant tenant,
      OAuthSession oAuthSession,
      MfaInteractionType type,
      Map<String, Object> params,
      UserRepository userRepository) {

    switch (type.name()) {
      case "WEBAUTHN_REGISTRATION_CHALLENGE" -> {
        WebAuthnSession webAuthnSession = challengeRegistration(tenant);

        Map<String, Object> response = new HashMap<>();
        response.put("challenge", webAuthnSession.challengeAsString());

        return new MfaInteractionResult(
            type, response, DefaultEventType.webauthn_registration_challenge);
      }
      case "WEBAUTHN_REGISTRATION" -> {
        String request = (String) params.get("request");
        String userId = oAuthSession.user().sub();

        WebAuthnCredential webAuthnCredential = verifyRegistration(tenant, userId, request);
        Map<String, Object> response = new HashMap<>();
        response.put("registration", webAuthnCredential.toMap());

        return new MfaInteractionResult(
            type, response, DefaultEventType.webauthn_registration_success);
      }
      case "WEBAUTHN_AUTHENTICATION_CHALLENGE" -> {
        WebAuthnSession webAuthnSession = challengeAuthentication(tenant);

        Map<String, Object> response = new HashMap<>();
        response.put("challenge", webAuthnSession.challengeAsString());

        return new MfaInteractionResult(
            type, response, DefaultEventType.webauthn_authentication_challenge);
      }
      case "WEBAUTHN_AUTHENTICATION" -> {
        String request = (String) params.get("request");

        User user = verifyAuthentication(tenant, userRepository, request);
        Authentication authentication =
            new Authentication()
                .setTime(SystemDateTime.now())
                .addMethods(new ArrayList<>(List.of("hwk")))
                .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

        Map<String, Object> response = new HashMap<>();
        response.put("user", user.toMap());
        response.put("authentication", authentication.toMap());

        return new MfaInteractionResult(
            type, user, authentication, response, DefaultEventType.webauthn_authentication_success);
      }
    }
    throw new MfaInteractorUnSupportedException(
        "WebAuthnInteractor is not supported  type " + type);
  }

  private WebAuthnSession challengeRegistration(Tenant tenant) {
    webAuthnConfigurationService.get(tenant);

    return webAuthnSessionService.start();
  }

  private WebAuthnCredential verifyRegistration(Tenant tenant, String userId, String request) {

    WebAuthnConfiguration configuration = webAuthnConfigurationService.get(tenant);
    WebAuthnSession session = webAuthnSessionService.get();

    WebAuthnRegistrationManager manager =
        new WebAuthnRegistrationManager(configuration, session, request, userId);

    WebAuthnCredential webAuthnCredential = manager.verifyAndCreateCredential();

    // FIXME change timing registration
    webAuthnCredentialService.register(webAuthnCredential);

    return webAuthnCredential;
  }

  private WebAuthnSession challengeAuthentication(Tenant tenant) {
    webAuthnConfigurationService.get(tenant);

    return webAuthnSessionService.start();
  }

  private User verifyAuthentication(Tenant tenant, UserRepository userRepository, String request) {

    WebAuthnConfiguration configuration = webAuthnConfigurationService.get(tenant);
    WebAuthnSession session = webAuthnSessionService.get();

    WebAuthnAuthenticationManager manager =
        new WebAuthnAuthenticationManager(configuration, session, request);

    String extractUserId = manager.extractUserId();
    WebAuthnCredentials webAuthnCredentials = webAuthnCredentialService.findAll(extractUserId);

    manager.verify(webAuthnCredentials);

    return userRepository.get(extractUserId);
  }
}
