package org.idp.server.core.authentication.device;

import java.util.Map;
import org.idp.server.core.notification.device.NotificationChannel;
import org.idp.server.basic.exception.UnSupportedException;

public class AuthenticationDeviceNotifiers {

  Map<NotificationChannel, AuthenticationDeviceNotifier> notifiers;

  public AuthenticationDeviceNotifiers(
      Map<NotificationChannel, AuthenticationDeviceNotifier> notifiers) {
    this.notifiers = notifiers;
  }

  public AuthenticationDeviceNotifier get(NotificationChannel channel) {
    AuthenticationDeviceNotifier notifier = notifiers.get(channel);

    if (notifier == null) {
      throw new UnSupportedException(
          "Authentication device notifier " + channel.name() + " not supported");
    }

    return notifier;
  }
}
