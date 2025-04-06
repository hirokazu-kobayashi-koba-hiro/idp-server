package org.idp.server.core.authentication.password;

import org.idp.server.core.authentication.*;
import org.idp.server.core.oauth.identity.PasswordEncodeDelegation;

public class PasswordRegistrationFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.PASSWORD_REGISTRATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    PasswordEncodeDelegation passwordEncodeDelegation =
        container.resolve(PasswordEncodeDelegation.class);
    return new PasswordRegistrationInteractor(passwordEncodeDelegation);
  }
}
