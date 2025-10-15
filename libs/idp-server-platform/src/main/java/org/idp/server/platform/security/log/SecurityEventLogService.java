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

package org.idp.server.platform.security.log;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.repository.SecurityEventCommandRepository;

public class SecurityEventLogService {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventLogService.class);
  private final Map<SecurityEventLogFormatter.Format, SecurityEventLogFormatter> formatters =
      new HashMap<>();
  private final SecurityEventCommandRepository repository;

  public SecurityEventLogService(SecurityEventCommandRepository repository) {
    this.repository = repository;
    formatters.put(
        SecurityEventLogFormatter.Format.STRUCTURED_JSON, new StructuredJsonLogFormatter());
    formatters.put(SecurityEventLogFormatter.Format.SIMPLE, new SimpleLogFormatter());
  }

  public void logEvent(Tenant tenant, SecurityEvent securityEvent) {
    SecurityEventLogConfiguration config = tenant.securityEventLogConfiguration();

    if (!config.isEnabled()) {
      return;
    }

    logToConsole(securityEvent, config);

    if (config.isPersistenceEnabled()) {
      persist(tenant, securityEvent);
    }
  }

  private void logToConsole(SecurityEvent securityEvent, SecurityEventLogConfiguration config) {
    SecurityEventLogFormatter formatter = getFormatter(config.getFormat());
    String logMessage = formatter.format(securityEvent, config);
    SecurityEventLogLevel logLevel = determineLogLevel(securityEvent);

    switch (logLevel) {
      case ERROR -> log.error(logMessage);
      case WARN -> log.warn(logMessage);
      case INFO -> log.info(logMessage);
      case DEBUG -> log.debug(logMessage);
    }
  }

  private void persist(Tenant tenant, SecurityEvent securityEvent) {
    try {
      repository.register(tenant, securityEvent);
    } catch (Exception e) {
      log.error("Failed to persist security event to database: error={}", e.getMessage(), e);
    }
  }

  private SecurityEventLogFormatter getFormatter(SecurityEventLogFormatter.Format format) {
    SecurityEventLogFormatter formatter = formatters.get(format);
    if (formatter == null) {
      formatter = formatters.get(SecurityEventLogFormatter.Format.SIMPLE);
    }
    return formatter;
  }

  private SecurityEventLogLevel determineLogLevel(SecurityEvent securityEvent) {
    String eventType = securityEvent.type().value();

    if (eventType.endsWith("_failure") || eventType.endsWith("_error")) {
      return SecurityEventLogLevel.WARN;
    }
    if (eventType.contains("_critical_")) {
      return SecurityEventLogLevel.ERROR;
    }
    return SecurityEventLogLevel.INFO;
  }
}
