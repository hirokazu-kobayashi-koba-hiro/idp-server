package org.idp.server.core.security.handler;

import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.*;
import org.idp.server.core.security.event.SecurityEventRepository;
import org.idp.server.core.security.hook.*;

public class SecurityEventHandler {

  SecurityEventRepository securityEventRepository;
  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventHandler.class);

  public SecurityEventHandler(
      SecurityEventRepository securityEventRepository,
      SecurityEventHooks securityEventHooks,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository) {
    this.securityEventRepository = securityEventRepository;
    this.securityEventHooks = securityEventHooks;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
  }

  public void handle(Tenant tenant, SecurityEvent securityEvent) {

    securityEventRepository.register(tenant, securityEvent);

    SecurityEventHookConfigurations securityEventHookConfigurations =
        securityEventHookConfigurationQueryRepository.find(tenant);

    securityEventHookConfigurations.forEach(
        hookConfiguration -> {
          SecurityEventHookExecutor securityEventHookExecutor =
              securityEventHooks.get(hookConfiguration.hookType());

          if (securityEventHookExecutor.shouldNotExecute(
              tenant, securityEvent, hookConfiguration)) {
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

          securityEventHookExecutor.execute(tenant, securityEvent, hookConfiguration);
        });
  }
}
