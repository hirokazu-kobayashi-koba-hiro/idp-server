package org.idp.server.authenticators;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authenticators.webauthn.*;
import org.idp.server.core.mfa.MfaInteractionResult;
import org.idp.server.core.mfa.MfaInteractionType;
import org.idp.server.core.mfa.MfaInteractor;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class WebAuthnRegistrationInteractor implements MfaInteractor {

  WebAuthnConfigurationRepository configurationRepository;
  WebAuthnSessionRepository sessionRepository;
  WebAuthnCredentialRepository credentialRepository;

  public WebAuthnRegistrationInteractor(
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
    String userId = oAuthSession.user().sub();

    WebAuthnConfiguration configuration = configurationRepository.get(tenant);
    WebAuthnSession session = sessionRepository.get();

    WebAuthnRegistrationManager manager =
        new WebAuthnRegistrationManager(configuration, session, request, userId);

    WebAuthnCredential webAuthnCredential = manager.verifyAndCreateCredential();

    credentialRepository.register(webAuthnCredential);

    Map<String, Object> response = new HashMap<>();
    response.put("registration", webAuthnCredential.toMap());

    return new MfaInteractionResult(type, response, DefaultSecurityEventType.webauthn_registration_success);
  }
}
