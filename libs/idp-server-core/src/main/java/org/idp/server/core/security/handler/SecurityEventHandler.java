package org.idp.server.core.security.handler;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.*;
import org.idp.server.core.security.hook.*;
import org.idp.server.core.security.repository.SecurityEventCommandRepository;
import org.idp.server.core.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.security.repository.SecurityEventHookResultCommandRepository;

public class SecurityEventHandler {

  SecurityEventCommandRepository securityEventCommandRepository;
  SecurityEventHookResultCommandRepository resultsCommandRepository;
  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventHandler.class);

  public SecurityEventHandler(SecurityEventHooks securityEventHooks, SecurityEventCommandRepository securityEventCommandRepository, SecurityEventHookResultCommandRepository resultsCommandRepository, SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository) {
    this.securityEventHooks = securityEventHooks;
    this.securityEventCommandRepository = securityEventCommandRepository;
    this.resultsCommandRepository = resultsCommandRepository;
    this.securityEventHookConfigurationQueryRepository = securityEventHookConfigurationQueryRepository;
  }

  public void handle(Tenant tenant, SecurityEvent securityEvent) {

    securityEventCommandRepository.register(tenant, securityEvent);

    SecurityEventHookConfigurations securityEventHookConfigurations = securityEventHookConfigurationQueryRepository.find(tenant);

    List<SecurityEventHookResult> results = new ArrayList<>();
    for (SecurityEventHookConfiguration hookConfiguration : securityEventHookConfigurations) {

      SecurityEventHookExecutor securityEventHookExecutor = securityEventHooks.get(hookConfiguration.hookType());

      if (securityEventHookExecutor.shouldNotExecute(tenant, securityEvent, hookConfiguration)) {
        return;
      }

      log.info(String.format("security event hook execution trigger: %s, type: %s tenant: %s client: %s user: %s, ", securityEvent.type().value(), hookConfiguration.hookType().name(), securityEvent.tenantIdentifierValue(), securityEvent.clientIdentifierValue(), securityEvent.userSub()));

      SecurityEventHookResult hookResult = securityEventHookExecutor.execute(tenant, securityEvent, hookConfiguration);
      results.add(hookResult);
    }

    if (!results.isEmpty()) {
      resultsCommandRepository.register(tenant, securityEvent, results);
    }
  }
}
