package org.idp.server.authenticators;

import org.idp.server.authenticators.webauthn.WebAuthnConfigurationRepository;
import org.idp.server.authenticators.webauthn.WebAuthnCredentialRepository;
import org.idp.server.authenticators.webauthn.WebAuthnSessionRepository;
import org.idp.server.core.mfa.*;

public class WebAuthnRegistrationIChallengeInteractorFactory implements MfaInteractorFactory {

  @Override
  public MfaInteractionType type() {
    return StandardMfaInteractionType.WEBAUTHN_REGISTRATION_CHALLENGE.toType();
  }

  @Override
  public MfaInteractor create(MfaDependencyContainer container) {
    WebAuthnConfigurationRepository configurationRepository =
        container.resolve(WebAuthnConfigurationRepository.class);
    WebAuthnSessionRepository sessionRepository =
        container.resolve(WebAuthnSessionRepository.class);
    WebAuthnCredentialRepository credentialRepository =
        container.resolve(WebAuthnCredentialRepository.class);

    return new WebAuthnRegistrationChallengeInteractor(
        configurationRepository, sessionRepository, credentialRepository);
  }
}
