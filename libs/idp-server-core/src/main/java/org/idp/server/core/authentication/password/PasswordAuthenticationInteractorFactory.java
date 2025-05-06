package org.idp.server.core.authentication.password;

import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationInteractorFactory;
import org.idp.server.core.identity.authentication.PasswordVerificationDelegation;

public class PasswordAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.PASSWORD_AUTHENTICATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    PasswordVerificationDelegation passwordVerificationDelegation = container.resolve(PasswordVerificationDelegation.class);
    return new PasswordAuthenticationInteractor(passwordVerificationDelegation);
  }
}
