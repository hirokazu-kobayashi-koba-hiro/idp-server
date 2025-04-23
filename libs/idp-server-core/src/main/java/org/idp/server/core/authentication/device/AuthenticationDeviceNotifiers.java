package org.idp.server.core.authentication.device;

import java.util.Map;
import org.idp.server.core.notification.push.PushNotificationChannel;
import org.idp.server.core.type.exception.UnSupportedException;

public class AuthenticationDeviceNotifiers {

  Map<PushNotificationChannel, AuthenticationDeviceNotifier> notifiers;

  public AuthenticationDeviceNotifiers(
      Map<PushNotificationChannel, AuthenticationDeviceNotifier> notifiers) {
    this.notifiers = notifiers;
  }

  public AuthenticationDeviceNotifier get(PushNotificationChannel channel) {
    AuthenticationDeviceNotifier notifier = notifiers.get(channel);

    if (notifier == null) {
      throw new UnSupportedException(
          "Authentication device notifier " + channel.name() + " not supported");
    }

    return notifier;
  }
}
