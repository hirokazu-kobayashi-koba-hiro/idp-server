package org.idp.server.core.authentication.device;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import org.idp.server.core.authentication.notification.device.NotificationChannel;

public class AuthenticationDeviceNotifiersLoader {

  private static final Logger log =
      Logger.getLogger(AuthenticationDeviceNotifiersLoader.class.getName());

  public static AuthenticationDeviceNotifiers load() {
    Map<NotificationChannel, AuthenticationDeviceNotifier> notifiers = new HashMap<>();
    ServiceLoader<AuthenticationDeviceNotifier> loader =
        ServiceLoader.load(AuthenticationDeviceNotifier.class);

    for (AuthenticationDeviceNotifier notifier : loader) {
      notifiers.put(notifier.chanel(), notifier);
      log.info(
          String.format(
              "Dynamic Registered AuthenticationDeviceNotifier %s", notifier.chanel().name()));
    }

    return new AuthenticationDeviceNotifiers(notifiers);
  }
}
