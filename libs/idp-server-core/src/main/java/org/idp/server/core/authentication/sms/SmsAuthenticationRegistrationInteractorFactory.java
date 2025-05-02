package org.idp.server.core.authentication.sms;


import org.idp.server.core.authentication.*;

public class SmsAuthenticationRegistrationInteractorFactory implements AuthenticationInteractorFactory {

    @Override
    public AuthenticationInteractionType type() {
        return StandardAuthenticationInteraction.SMS_AUTHENTICATION_REGISTRATION.toType();
    }

    @Override
    public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

        SmsAuthenticationExecutors executors = container.resolve(SmsAuthenticationExecutors.class);
        AuthenticationConfigurationQueryRepository configurationQueryRepository = container.resolve(AuthenticationConfigurationQueryRepository.class);
        return new SmsAuthenticationRegistrationInteractor(executors, configurationQueryRepository);
    }
}
