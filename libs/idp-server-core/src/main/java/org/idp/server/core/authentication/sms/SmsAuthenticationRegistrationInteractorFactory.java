package org.idp.server.core.authentication.sms;

import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationInteractorFactory;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;

public class SmsAuthenticationRegistrationInteractorFactory
    implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.SMS_AUTHENTICATION_REGISTRATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    SmsAuthenticationExecutors executors = container.resolve(SmsAuthenticationExecutors.class);
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new SmsAuthenticationRegistrationInteractor(executors, configurationQueryRepository);
  }
}
