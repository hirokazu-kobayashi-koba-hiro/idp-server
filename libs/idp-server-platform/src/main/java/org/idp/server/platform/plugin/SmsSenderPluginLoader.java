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

package org.idp.server.platform.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.notification.sms.SmsSender;
import org.idp.server.platform.notification.sms.SmsSenderFactory;
import org.idp.server.platform.notification.sms.SmsSenderType;
import org.idp.server.platform.notification.sms.SmsSenders;

public class SmsSenderPluginLoader
    extends DependencyAwarePluginLoader<SmsSender, SmsSenderFactory> {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(SmsSenderPluginLoader.class);

  /**
   * Loads SmsSender instances with dependency injection support. Supports both new Factory pattern
   * (recommended) and legacy direct SPI (for compatibility).
   *
   * @param container the dependency injection container
   * @return SmsSenders instance containing all loaded SMS senders
   */
  public static SmsSenders load(ApplicationComponentDependencyContainer container) {

    Map<SmsSenderType, SmsSender> senders = new HashMap<>();

    // New approach: Factory pattern with DI support (recommended)
    List<SmsSenderFactory> factories = loadFromInternalModule(SmsSenderFactory.class);
    for (SmsSenderFactory factory : factories) {
      SmsSender sender = factory.create(container);
      senders.put(factory.smsSenderType(), sender);
      log.info(
          "Loaded SMS sender via Factory pattern (with DI support): {}",
          factory.smsSenderType().name());
    }

    List<SmsSenderFactory> externalFactories = loadFromExternalModule(SmsSenderFactory.class);
    for (SmsSenderFactory factory : externalFactories) {
      SmsSender sender = factory.create(container);
      senders.put(factory.smsSenderType(), sender);
      log.info(
          "Loaded SMS sender via Factory pattern (external, with DI support): {}",
          factory.smsSenderType().name());
    }

    // Legacy approach: Direct SPI for backward compatibility (deprecated)
    Map<SmsSenderType, SmsSender> legacySenders = loadLegacySmsSenders();
    for (Map.Entry<SmsSenderType, SmsSender> entry : legacySenders.entrySet()) {
      if (!senders.containsKey(entry.getKey())) {
        senders.put(entry.getKey(), entry.getValue());
        log.warn(
            "Using legacy SmsSender without DI: {} -> {} - Consider migrating to SmsSenderFactory",
            entry.getKey().name(),
            entry.getValue().getClass().getName());
      } else {
        log.info("Factory version takes precedence over legacy SPI for: {}", entry.getKey().name());
      }
    }

    if (senders.isEmpty()) {
      log.warn("No SmsSender instances loaded. Check SPI configuration.");
    } else {
      log.info("Total {} SMS sender(s) loaded successfully", senders.size());
    }

    return new SmsSenders(senders);
  }

  /**
   * Legacy load method without DI container (backward compatibility). This method creates
   * SmsSenders without dependency injection support.
   *
   * @deprecated Use {@link #load(ApplicationComponentDependencyContainer)} for DI support
   * @return SmsSenders instance with legacy loading
   */
  @Deprecated
  public static SmsSenders load() {
    log.warn(
        "Using deprecated SmsSenderPluginLoader.load() without DI container. "
            + "OAuth token caching will not be available for SmsSender instances.");

    Map<SmsSenderType, SmsSender> senders = loadLegacySmsSenders();
    return new SmsSenders(senders);
  }

  private static Map<SmsSenderType, SmsSender> loadLegacySmsSenders() {
    Map<SmsSenderType, SmsSender> senders = new HashMap<>();

    // Load from internal modules
    List<SmsSender> internalSmsSenders = loadFromInternalModule(SmsSender.class);
    for (SmsSender smsSender : internalSmsSenders) {
      senders.put(smsSender.type(), smsSender);
      log.info("Loaded SMS sender via legacy SPI (internal): {}", smsSender.type().name());
    }

    // Load from external modules
    List<SmsSender> externalSmsSenders = loadFromExternalModule(SmsSender.class);
    for (SmsSender smsSender : externalSmsSenders) {
      senders.put(smsSender.type(), smsSender);
      log.info("Loaded SMS sender via legacy SPI (external): {}", smsSender.type().name());
    }

    return senders;
  }
}
