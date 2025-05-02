package org.idp.server.core.authentication.sms;

import org.idp.server.core.authentication.*;

public class SmsAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

    @Override
    public AuthenticationInteractionType type() {
        return StandardAuthenticationInteraction.SMS_AUTHENTICATION.toType();
    }

    @Override
    public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

        SmsAuthenticationExecutors executors = container.resolve(SmsAuthenticationExecutors.class);
        AuthenticationConfigurationQueryRepository configurationQueryRepository = container.resolve(AuthenticationConfigurationQueryRepository.class);
        return new SmsAuthenticationInteractor(executors, configurationQueryRepository);
    }
}
