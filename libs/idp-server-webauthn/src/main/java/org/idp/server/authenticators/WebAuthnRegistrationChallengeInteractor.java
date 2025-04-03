package org.idp.server.authenticators;

import org.idp.server.authenticators.webauthn.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.mfa.MfaInteractionResult;
import org.idp.server.core.mfa.MfaInteractionType;
import org.idp.server.core.mfa.MfaInteractor;
import org.idp.server.core.mfa.MfaInteractorUnSupportedException;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.sharedsignal.DefaultEventType;
import org.idp.server.core.tenant.Tenant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebAuthnRegistrationChallengeInteractor implements MfaInteractor {

  WebAuthnConfigurationRepository configurationRepository;
  WebAuthnSessionRepository sessionRepository;
  WebAuthnCredentialRepository credentialRepository;

  public WebAuthnRegistrationChallengeInteractor(
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

    configurationRepository.get(tenant);

    WebAuthnChallenge webAuthnChallenge = WebAuthnChallenge.generate();
    WebAuthnSession webAuthnSession = new WebAuthnSession(webAuthnChallenge);

    sessionRepository.register(webAuthnSession);

    Map<String, Object> response = new HashMap<>();
    response.put("challenge", webAuthnSession.challengeAsString());

    return new MfaInteractionResult(
            type, response, DefaultEventType.webauthn_registration_challenge);

  }


}
