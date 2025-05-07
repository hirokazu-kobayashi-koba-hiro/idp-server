package org.idp.server.core.authentication.device;

import org.idp.server.core.authentication.AuthenticationInteractionType;
import org.idp.server.core.authentication.AuthenticationInteractor;
import org.idp.server.core.authentication.StandardAuthenticationInteraction;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationInteractorFactory;

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
