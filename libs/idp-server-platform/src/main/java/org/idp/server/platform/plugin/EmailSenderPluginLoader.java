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
import java.util.Map;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.notification.email.EmailSender;
import org.idp.server.platform.notification.email.EmailSenderFactory;
import org.idp.server.platform.notification.email.EmailSenders;

public class EmailSenderPluginLoader
    extends DependencyAwarePluginLoader<EmailSender, EmailSenderFactory> {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(EmailSenderPluginLoader.class);

  /**
   * Loads EmailSender instances with dependency injection support. Supports both new Factory
   * pattern (recommended) and legacy direct SPI (for compatibility).
   *
   * @param container the dependency injection container
   * @return EmailSenders instance containing all loaded email senders
   */
  public static EmailSenders load(ApplicationComponentDependencyContainer container) {

    Map<String, EmailSender> senders = new HashMap<>();

    // New approach: Factory pattern with DI support (recommended)
    Map<String, EmailSender> factorySenders =
        loadWithDependencies(EmailSenderFactory.class, container);
    senders.putAll(factorySenders);
    log.info(
        "Loaded {} email senders via Factory pattern (with DI support)", factorySenders.size());

    // Legacy approach: Direct SPI for backward compatibility (deprecated)
    Map<String, EmailSender> legacySenders =
        loadLegacyComponents(EmailSender.class, EmailSender::function);
    for (Map.Entry<String, EmailSender> entry : legacySenders.entrySet()) {
      if (!senders.containsKey(entry.getKey())) {
        senders.put(entry.getKey(), entry.getValue());
        log.warn(
            "Using legacy EmailSender without DI: {} -> {} - Consider migrating to EmailSenderFactory",
            entry.getKey(),
            entry.getValue().getClass().getName());
      } else {
        log.info("Factory version takes precedence over legacy SPI for: {}", entry.getKey());
      }
    }

    if (senders.isEmpty()) {
      log.warn("No EmailSender instances loaded. Check SPI configuration.");
    } else {
      log.info("Total {} email sender(s) loaded successfully", senders.size());
    }

    return new EmailSenders(senders);
  }

  /**
   * Legacy load method without DI container (backward compatibility). This method creates
   * EmailSenders without dependency injection support.
   *
   * @deprecated Use {@link #load(ApplicationComponentDependencyContainer)} for DI support
   * @return EmailSenders instance with legacy loading
   */
  @Deprecated
  public static EmailSenders load() {
    log.warn(
        "Using deprecated EmailSenderPluginLoader.load() without DI container. "
            + "OAuth token caching will not be available for EmailSender instances.");

    Map<String, EmailSender> senders =
        loadLegacyComponents(EmailSender.class, EmailSender::function);
    return new EmailSenders(senders);
  }
}
