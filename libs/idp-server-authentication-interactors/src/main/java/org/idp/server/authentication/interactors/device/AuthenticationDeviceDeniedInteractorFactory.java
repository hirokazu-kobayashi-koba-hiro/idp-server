package org.idp.server.authentication.interactors.device;

import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;
import org.idp.server.core.oidc.authentication.StandardAuthenticationInteraction;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;

public class AuthenticationDeviceDeniedInteractorFactory
    implements AuthenticationInteractorFactory {
  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.AUTHENTICATION_DEVICE_DENY.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {
    return new AuthenticationDeviceDeniedInteractor();
  }
}
