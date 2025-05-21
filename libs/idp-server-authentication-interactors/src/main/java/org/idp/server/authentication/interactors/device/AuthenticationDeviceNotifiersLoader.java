package org.idp.server.authentication.interactors.device;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.identity.device.NotificationChannel;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthenticationDeviceNotifiersLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationDeviceNotifiersLoader.class);

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
