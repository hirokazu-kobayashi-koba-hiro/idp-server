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
import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifierFactory;
import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifiers;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.notification.NotificationChannel;
import org.idp.server.platform.plugin.DependencyAwarePluginLoader;

public class AuthenticationDeviceNotifiersPluginLoader
    extends DependencyAwarePluginLoader<
        AuthenticationDeviceNotifier, AuthenticationDeviceNotifierFactory> {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationDeviceNotifiersPluginLoader.class);

  /**
   * Loads AuthenticationDeviceNotifier instances with dependency injection support. Supports both
   * new Factory pattern (recommended) and legacy direct SPI (for compatibility).
   *
   * @param container the dependency injection container
   * @return AuthenticationDeviceNotifiers instance containing all loaded device notifiers
   */
  public static AuthenticationDeviceNotifiers load(
      ApplicationComponentDependencyContainer container) {

    Map<NotificationChannel, AuthenticationDeviceNotifier> notifiers = new HashMap<>();

    // New approach: Factory pattern with DI support (recommended)
    List<AuthenticationDeviceNotifierFactory> factories =
        loadFromInternalModule(AuthenticationDeviceNotifierFactory.class);
    for (AuthenticationDeviceNotifierFactory factory : factories) {
      AuthenticationDeviceNotifier notifier = factory.create(container);
      notifiers.put(factory.notificationChannel(), notifier);
      log.info(
          "Loaded AuthenticationDeviceNotifier via Factory pattern (with DI support): {}",
          factory.notificationChannel().name());
    }

    List<AuthenticationDeviceNotifierFactory> externalFactories =
        loadFromExternalModule(AuthenticationDeviceNotifierFactory.class);
    for (AuthenticationDeviceNotifierFactory factory : externalFactories) {
      AuthenticationDeviceNotifier notifier = factory.create(container);
      notifiers.put(factory.notificationChannel(), notifier);
      log.info(
          "Loaded AuthenticationDeviceNotifier via Factory pattern (external, with DI support): {}",
          factory.notificationChannel().name());
    }

    // Legacy approach: Direct SPI for backward compatibility (deprecated)
    Map<NotificationChannel, AuthenticationDeviceNotifier> legacyNotifiers = loadLegacyNotifiers();
    for (Map.Entry<NotificationChannel, AuthenticationDeviceNotifier> entry :
        legacyNotifiers.entrySet()) {
      if (!notifiers.containsKey(entry.getKey())) {
        notifiers.put(entry.getKey(), entry.getValue());
        log.warn(
            "Using legacy AuthenticationDeviceNotifier without DI: {} -> {} - Consider migrating to AuthenticationDeviceNotifierFactory",
            entry.getKey().name(),
            entry.getValue().getClass().getName());
      } else {
        log.info("Factory version takes precedence over legacy SPI for: {}", entry.getKey().name());
      }
    }

    if (notifiers.isEmpty()) {
      log.warn("No AuthenticationDeviceNotifier instances loaded. Check SPI configuration.");
    } else {
      log.info("Total {} AuthenticationDeviceNotifier(s) loaded successfully", notifiers.size());
    }

    return new AuthenticationDeviceNotifiers(notifiers);
  }

  /**
   * Legacy load method without DI container (backward compatibility). This method creates
   * AuthenticationDeviceNotifiers without dependency injection support.
   *
   * @deprecated Use {@link #load(ApplicationComponentDependencyContainer)} for DI support
   * @return AuthenticationDeviceNotifiers instance with legacy loading
   */
  @Deprecated
  public static AuthenticationDeviceNotifiers load() {
    log.warn(
        "Using deprecated AuthenticationDeviceNotifiersPluginLoader.load() without DI container. "
            + "EmailSenders, SmsSenders, and OAuth token caching will not be available for AuthenticationDeviceNotifier instances.");

    Map<NotificationChannel, AuthenticationDeviceNotifier> notifiers = loadLegacyNotifiers();
    return new AuthenticationDeviceNotifiers(notifiers);
  }

  private static Map<NotificationChannel, AuthenticationDeviceNotifier> loadLegacyNotifiers() {
    Map<NotificationChannel, AuthenticationDeviceNotifier> notifiers = new HashMap<>();

    // Load from internal modules
    List<AuthenticationDeviceNotifier> internals =
        loadFromInternalModule(AuthenticationDeviceNotifier.class);
    for (AuthenticationDeviceNotifier notifier : internals) {
      notifiers.put(notifier.chanel(), notifier);
      log.info(
          "Loaded AuthenticationDeviceNotifier via legacy SPI (internal): {}",
          notifier.chanel().name());
    }

    // Load from external modules
    List<AuthenticationDeviceNotifier> externals =
        loadFromExternalModule(AuthenticationDeviceNotifier.class);
    for (AuthenticationDeviceNotifier notifier : externals) {
      notifiers.put(notifier.chanel(), notifier);
      log.info(
          "Loaded AuthenticationDeviceNotifier via legacy SPI (external): {}",
          notifier.chanel().name());
    }

    return notifiers;
  }
}
