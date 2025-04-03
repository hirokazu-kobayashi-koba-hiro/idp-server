package org.idp.server.authenticators;

import org.idp.server.authenticators.webauthn.WebAuthnConfigurationRepository;
import org.idp.server.authenticators.webauthn.WebAuthnCredentialRepository;
import org.idp.server.authenticators.webauthn.WebAuthnSessionRepository;
import org.idp.server.core.mfa.*;

public class WebAuthnAuthenticationChallengeInteractorFactory implements MfaInteractorFactory {

    @Override
    public MfaInteractionType type() {
        return StandardMfaInteractionType.WEBAUTHN_AUTHENTICATION_CHALLENGE.toType();
    }

    @Override
    public MfaInteractor create(MfaDependencyContainer container) {
        WebAuthnConfigurationRepository configurationRepository = container.resolve(WebAuthnConfigurationRepository.class);
        WebAuthnSessionRepository sessionRepository = container.resolve(WebAuthnSessionRepository.class);
        WebAuthnCredentialRepository credentialRepository = container.resolve(WebAuthnCredentialRepository.class);

        return new WebAuthnAuthenticationChallengeInteractor(configurationRepository, sessionRepository, credentialRepository);
    }
}
