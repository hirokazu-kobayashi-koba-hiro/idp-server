package org.idp.server.authenticators;

import org.idp.server.authenticators.webauthn.WebAuthnCredentialRepository;
import org.idp.server.core.mfa.*;
import org.idp.server.core.mfa.MfaConfigurationQueryRepository;

public class WebAuthnRegistrationInteractorFactory implements MfaInteractorFactory {

  @Override
  public MfaInteractionType type() {
    return StandardMfaInteraction.WEBAUTHN_REGISTRATION.toType();
  }

  @Override
  public MfaInteractor create(MfaDependencyContainer container) {
    MfaConfigurationQueryRepository configurationRepository =
        container.resolve(MfaConfigurationQueryRepository.class);
    MfaTransactionQueryRepository transactionQueryRepository =
        container.resolve(MfaTransactionQueryRepository.class);
    WebAuthnCredentialRepository credentialRepository =
        container.resolve(WebAuthnCredentialRepository.class);

    return new WebAuthnRegistrationInteractor(
        configurationRepository, transactionQueryRepository, credentialRepository);
  }
}
