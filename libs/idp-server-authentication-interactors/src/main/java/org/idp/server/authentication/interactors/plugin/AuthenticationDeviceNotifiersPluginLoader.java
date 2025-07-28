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

package org.idp.server.authentication.interactors.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifier;
import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifiers;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.notification.NotificationChannel;
import org.idp.server.platform.plugin.PluginLoader;

public class AuthenticationDeviceNotifiersPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationDeviceNotifiersPluginLoader.class);

  public static AuthenticationDeviceNotifiers load() {
    Map<NotificationChannel, AuthenticationDeviceNotifier> notifiers = new HashMap<>();

    List<AuthenticationDeviceNotifier> internals =
        loadFromInternalModule(AuthenticationDeviceNotifier.class);
    for (AuthenticationDeviceNotifier notifier : internals) {
      notifiers.put(notifier.chanel(), notifier);
      log.info(
          String.format(
              "Dynamic Registered internal AuthenticationDeviceNotifier %s",
              notifier.chanel().name()));
    }

    List<AuthenticationDeviceNotifier> externals =
        loadFromExternalModule(AuthenticationDeviceNotifier.class);
    for (AuthenticationDeviceNotifier notifier : externals) {
      notifiers.put(notifier.chanel(), notifier);
      log.info(
          String.format(
              "Dynamic Registered externals AuthenticationDeviceNotifier %s",
              notifier.chanel().name()));
    }

    return new AuthenticationDeviceNotifiers(notifiers);
  }
}
