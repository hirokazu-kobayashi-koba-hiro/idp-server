package org.idp.server.authentication.interactors.device;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;
import org.idp.server.core.oidc.authentication.StandardAuthenticationInteraction;

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
