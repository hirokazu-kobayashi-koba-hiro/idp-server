package org.idp.server.core.authentication.device;

import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.notification.device.NotificationChannel;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.oauth.identity.device.AuthenticationDevice;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

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
      UserRepository userRepository) {

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
