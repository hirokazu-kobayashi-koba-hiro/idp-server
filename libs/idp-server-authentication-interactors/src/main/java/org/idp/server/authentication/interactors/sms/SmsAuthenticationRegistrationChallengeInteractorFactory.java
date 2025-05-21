package org.idp.server.authentication.interactors.sms;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;
import org.idp.server.core.oidc.authentication.StandardAuthenticationInteraction;

public class SmsAuthenticationRegistrationChallengeInteractorFactory
    implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.SMS_AUTHENTICATION_REGISTRATION_CHALLENGE.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    SmsAuthenticationExecutors executors = container.resolve(SmsAuthenticationExecutors.class);
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new SmsAuthenticationRegistrationChallengeInteractor(
        executors, configurationQueryRepository);
  }
}
