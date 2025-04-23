package org.idp.server.core.authentication.device;

import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.notification.push.PushNotificationChannel;
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

    // TODO
    AuthenticationDevice authenticationDevice = new AuthenticationDevice();
    PushNotificationChannel channel = new PushNotificationChannel("fcm");

    AuthenticationDeviceNotifier notifier = authenticationDeviceNotifiers.get(channel);

    notifier.notify(authenticationDevice);

    AuthenticationInteractionStatus status = AuthenticationInteractionStatus.SUCCESS;
    Map<String, Object> response = Map.of();
    DefaultSecurityEventType eventType =
        DefaultSecurityEventType.authentication_device_notification_success;
    return new AuthenticationInteractionRequestResult(status, type, response, eventType);
  }
}
