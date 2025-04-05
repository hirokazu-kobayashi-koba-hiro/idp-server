package org.idp.server.authenticators;

import org.idp.server.authenticators.webauthn.WebAuthnCredentialRepository;
import org.idp.server.core.mfa.*;
import org.idp.server.core.mfa.MfaConfigurationQueryRepository;

public class WebAuthnRegistrationIChallengeInteractorFactory implements MfaInteractorFactory {

  @Override
  public MfaInteractionType type() {
    return StandardMfaInteraction.WEBAUTHN_REGISTRATION_CHALLENGE.toType();
  }

  @Override
  public MfaInteractor create(MfaDependencyContainer container) {
    MfaConfigurationQueryRepository configurationRepository =
        container.resolve(MfaConfigurationQueryRepository.class);
    MfaTransactionCommandRepository transactionCommandRepository =
        container.resolve(MfaTransactionCommandRepository.class);
    WebAuthnCredentialRepository credentialRepository =
        container.resolve(WebAuthnCredentialRepository.class);

    return new WebAuthnRegistrationChallengeInteractor(
        configurationRepository, transactionCommandRepository, credentialRepository);
  }
}
