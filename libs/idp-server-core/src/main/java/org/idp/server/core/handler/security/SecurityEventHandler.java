package org.idp.server.core.handler.security;

import java.util.logging.Logger;
import org.idp.server.core.hook.*;
import org.idp.server.core.security.*;
import org.idp.server.core.security.ssf.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantRepository;

public class SecurityEventHandler {

  TenantRepository tenantRepository;
  SecurityEventRepository securityEventRepository;
  AuthenticationHooks authenticationHooks;
  HookConfigurationQueryRepository hookConfigurationQueryRepository;
  SharedSignalFrameworkConfigurationRepository sharedSignalFrameworkConfigurationRepository;
  SharedSignalEventGateway eventGateway;
  Logger log = Logger.getLogger(SecurityEventHandler.class.getName());

  public SecurityEventHandler(
      TenantRepository tenantRepository,
      SecurityEventRepository securityEventRepository,
      AuthenticationHooks authenticationHooks,
      HookConfigurationQueryRepository hookConfigurationQueryRepository,
      SharedSignalFrameworkConfigurationRepository sharedSignalFrameworkConfigurationRepository,
      SharedSignalEventGateway eventGateway) {
    this.tenantRepository = tenantRepository;
    this.securityEventRepository = securityEventRepository;
    this.authenticationHooks = authenticationHooks;
    this.hookConfigurationQueryRepository = hookConfigurationQueryRepository;
    this.sharedSignalFrameworkConfigurationRepository =
        sharedSignalFrameworkConfigurationRepository;
    this.eventGateway = eventGateway;
  }

  public void handle(SecurityEvent securityEvent) {
    securityEventRepository.register(securityEvent);

    Tenant tenant = tenantRepository.get(securityEvent.tenantIdentifier());

    HookTriggerType hookTriggerType = HookTriggerType.of(securityEvent.type().value());
    if (hookTriggerType.isDefined()) {

      HookConfigurations hookConfigurations =
          hookConfigurationQueryRepository.find(tenant, hookTriggerType);

      if (hookConfigurations.exists()) {
        HookRequest hookRequest = new HookRequest(securityEvent.toMap());

        hookConfigurations.forEach(
            hookConfiguration -> {
              log.info(
                  String.format(
                      "hook execution trigger: %s, type: %s tenant: %s client: %s user: %s, ",
                      hookConfiguration.triggerType().name(),
                      hookConfiguration.hookType().name(),
                      securityEvent.tenantIdentifierValue(),
                      securityEvent.clientId().value(),
                      securityEvent.user().id()));
              HookExecutor hookExecutor = authenticationHooks.get(hookConfiguration.hookType());
              hookExecutor.execute(
                  tenant, HookTriggerType.POST_LOGIN, hookRequest, hookConfiguration);
            });
      }
    }

    SecurityEventTokenEntityConvertor convertor = new SecurityEventTokenEntityConvertor(securityEvent);
    SecurityEventTokenEntity securityEventTokenEntity = convertor.convert();

    SharedSignalFrameworkConfiguration configuration =
        sharedSignalFrameworkConfigurationRepository.find(securityEvent.tokenIssuer().value());

    SharedSignalDecider decider = new SharedSignalDecider(configuration, securityEventTokenEntity);

    if (decider.decide()) {
      log.info(
          String.format(
              "notify shared signal (%s) to (%s)",
              securityEventTokenEntity.securityEventAsString(), configuration.issuer()));
      SecurityEventTokenCreator securityEventTokenCreator =
          new SecurityEventTokenCreator(securityEventTokenEntity, configuration.privateKey());
      SecurityEventToken securityEventToken = securityEventTokenCreator.create();

      eventGateway.send(
          new SharedSignalEventRequest(
              configuration.endpoint(), configuration.headers(), securityEventToken));
    }
  }
}
