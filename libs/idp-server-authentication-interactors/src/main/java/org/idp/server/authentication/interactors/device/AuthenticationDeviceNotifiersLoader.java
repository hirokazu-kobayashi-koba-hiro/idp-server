/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
