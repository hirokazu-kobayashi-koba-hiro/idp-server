package org.idp.server.core.authentication.password;

import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationInteractorFactory;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;

public class PasswordRegistrationFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.PASSWORD_REGISTRATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationConfigurationQueryRepository configurationQueryRepository = container.resolve(AuthenticationConfigurationQueryRepository.class);
    PasswordEncodeDelegation passwordEncodeDelegation = container.resolve(PasswordEncodeDelegation.class);
    return new PasswordRegistrationInteractor(configurationQueryRepository, passwordEncodeDelegation);
  }
}
