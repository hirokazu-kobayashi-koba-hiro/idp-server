package org.idp.server.authenticators;

import org.idp.server.authenticators.webauthn.WebAuthnCredentialRepository;
import org.idp.server.core.mfa.*;
import org.idp.server.core.mfa.MfaConfigurationQueryRepository;

public class WebAuthnAuthenticationChallengeInteractorFactory implements MfaInteractorFactory {

  @Override
  public MfaInteractionType type() {
    return StandardMfaInteraction.WEBAUTHN_AUTHENTICATION_CHALLENGE.toType();
  }

  @Override
  public MfaInteractor create(MfaDependencyContainer container) {
    MfaConfigurationQueryRepository configurationRepository =
        container.resolve(MfaConfigurationQueryRepository.class);
    MfaTransactionCommandRepository transactionCommandRepository =
        container.resolve(MfaTransactionCommandRepository.class);
    WebAuthnCredentialRepository credentialRepository =
        container.resolve(WebAuthnCredentialRepository.class);

    return new WebAuthnAuthenticationChallengeInteractor(
        configurationRepository, transactionCommandRepository, credentialRepository);
  }
}
