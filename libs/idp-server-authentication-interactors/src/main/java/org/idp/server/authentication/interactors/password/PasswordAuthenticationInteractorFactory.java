package org.idp.server.authentication.interactors.password;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;
import org.idp.server.core.oidc.authentication.StandardAuthenticationInteraction;
import org.idp.server.core.oidc.identity.authentication.PasswordVerificationDelegation;

public class PasswordAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.PASSWORD_AUTHENTICATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    PasswordVerificationDelegation passwordVerificationDelegation =
        container.resolve(PasswordVerificationDelegation.class);
    return new PasswordAuthenticationInteractor(passwordVerificationDelegation);
  }
}
