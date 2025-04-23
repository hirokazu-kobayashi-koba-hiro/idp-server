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

    AuthenticationDeviceNotifiers authenticationDeviceNotifiers =
        container.resolve(AuthenticationDeviceNotifiers.class);
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new AuthenticationDeviceNotificationInteractor(
        authenticationDeviceNotifiers, configurationQueryRepository);
  }
}
