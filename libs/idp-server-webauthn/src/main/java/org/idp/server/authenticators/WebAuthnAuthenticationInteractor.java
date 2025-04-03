package org.idp.server.authenticators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.authenticators.webauthn.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.mfa.*;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class WebAuthnAuthenticationInteractor implements MfaInteractor {

  WebAuthnConfigurationRepository configurationRepository;
  WebAuthnSessionRepository sessionRepository;
  WebAuthnCredentialRepository credentialRepository;

  public WebAuthnAuthenticationInteractor(
      WebAuthnConfigurationRepository configurationRepository,
      WebAuthnSessionRepository sessionRepository,
      WebAuthnCredentialRepository credentialRepository) {
    this.configurationRepository = configurationRepository;
    this.sessionRepository = sessionRepository;
    this.credentialRepository = credentialRepository;
  }

  @Override
  public MfaInteractionResult interact(
      Tenant tenant,
      OAuthSession oAuthSession,
      MfaInteractionType type,
      Map<String, Object> params,
      UserRepository userRepository) {

    String request = (String) params.get("request");

    WebAuthnConfiguration configuration = configurationRepository.get(tenant);
    WebAuthnSession session = sessionRepository.get();

    WebAuthnAuthenticationManager manager =
        new WebAuthnAuthenticationManager(configuration, session, request);

    String extractUserId = manager.extractUserId();
    WebAuthnCredentials webAuthnCredentials = credentialRepository.findAll(extractUserId);

    manager.verify(webAuthnCredentials);
    User user = userRepository.get(extractUserId);

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("hwk")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new MfaInteractionResult(
        type, user, authentication, response, DefaultSecurityEventType.webauthn_authentication_success);
  }
}
