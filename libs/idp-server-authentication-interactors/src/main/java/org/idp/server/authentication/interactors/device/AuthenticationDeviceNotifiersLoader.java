/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
