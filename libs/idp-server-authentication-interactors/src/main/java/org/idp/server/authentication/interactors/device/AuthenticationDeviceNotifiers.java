package org.idp.server.authentication.interactors.device;

import java.util.Map;
import org.idp.server.core.oidc.identity.device.NotificationChannel;
import org.idp.server.platform.exception.UnSupportedException;

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
