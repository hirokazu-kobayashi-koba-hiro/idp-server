/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
