package org.idp.server.core.authentication.device;

import org.idp.server.core.authentication.*;

public class AuthenticationDeviceNotificationInteractorFactory
    implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.AUTHENTICATION_DEVICE_NOTIFICATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {
    return new AuthenticationDeviceNotificationInteractor();
  }
}
