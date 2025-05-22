/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.security.handler;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventHookExecutor;
import org.idp.server.platform.security.SecurityEventHooks;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.repository.SecurityEventCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class SecurityEventHandler {

  SecurityEventCommandRepository securityEventCommandRepository;
  SecurityEventHookResultCommandRepository resultsCommandRepository;
  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventHandler.class);

  public SecurityEventHandler(
      SecurityEventHooks securityEventHooks,
      SecurityEventCommandRepository securityEventCommandRepository,
      SecurityEventHookResultCommandRepository resultsCommandRepository,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository) {
    this.securityEventHooks = securityEventHooks;
    this.securityEventCommandRepository = securityEventCommandRepository;
    this.resultsCommandRepository = resultsCommandRepository;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
  }

  public void handle(Tenant tenant, SecurityEvent securityEvent) {

    securityEventCommandRepository.register(tenant, securityEvent);

    SecurityEventHookConfigurations securityEventHookConfigurations =
        securityEventHookConfigurationQueryRepository.find(tenant);

    List<SecurityEventHookResult> results = new ArrayList<>();
    for (SecurityEventHookConfiguration hookConfiguration : securityEventHookConfigurations) {

      SecurityEventHookExecutor securityEventHookExecutor =
          securityEventHooks.get(hookConfiguration.hookType());

      if (securityEventHookExecutor.shouldNotExecute(tenant, securityEvent, hookConfiguration)) {
        return;
      }

      log.info(
          String.format(
              "security event hook execution trigger: %s, type: %s tenant: %s client: %s user: %s, ",
              securityEvent.type().value(),
              hookConfiguration.hookType().name(),
              securityEvent.tenantIdentifierValue(),
              securityEvent.clientIdentifierValue(),
              securityEvent.userSub()));

      SecurityEventHookResult hookResult =
          securityEventHookExecutor.execute(tenant, securityEvent, hookConfiguration);
      results.add(hookResult);
    }

    if (!results.isEmpty()) {
      resultsCommandRepository.register(tenant, securityEvent, results);
    }
  }
}
