package org.idp.server.core.authentication.legacy;

import org.idp.server.core.authentication.*;

public class LegacyIdServiceAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

    @Override
    public AuthenticationInteractionType type() {
        return new AuthenticationInteractionType("legacy");
    }

    @Override
    public AuthenticationInteractor create(AuthenticationDependencyContainer container) {
        
        AuthenticationConfigurationQueryRepository configurationRepository = container.resolve(AuthenticationConfigurationQueryRepository.class);
        return new LegacyIdServiceAuthenticationInteractor(configurationRepository);
    }
}
