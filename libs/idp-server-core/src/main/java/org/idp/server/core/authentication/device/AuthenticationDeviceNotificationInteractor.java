package org.idp.server.core.authentication.device;

import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.notification.device.NotificationChannel;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.device.AuthenticationDevice;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class AuthenticationDeviceNotificationInteractor implements AuthenticationInteractor {

  AuthenticationDeviceNotifiers authenticationDeviceNotifiers;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public AuthenticationDeviceNotificationInteractor(
      AuthenticationDeviceNotifiers authenticationDeviceNotifiers,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.authenticationDeviceNotifiers = authenticationDeviceNotifiers;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository) {

    AuthenticationDeviceNotificationConfiguration configuration =
        configurationQueryRepository.get(
            tenant, "authentication-device", AuthenticationDeviceNotificationConfiguration.class);

    // TODO
    User user = transaction.user();
    AuthenticationDevice authenticationDevice = user.getPrimaryAuthenticationDevice();
    NotificationChannel channel = new NotificationChannel("fcm");

    AuthenticationDeviceNotifier notifier = authenticationDeviceNotifiers.get(channel);

    notifier.notify(tenant, authenticationDevice, configuration);

    AuthenticationInteractionStatus status = AuthenticationInteractionStatus.SUCCESS;
    Map<String, Object> response = Map.of();
    DefaultSecurityEventType eventType =
        DefaultSecurityEventType.authentication_device_notification_success;
    return new AuthenticationInteractionRequestResult(status, type, response, eventType);
  }
}
