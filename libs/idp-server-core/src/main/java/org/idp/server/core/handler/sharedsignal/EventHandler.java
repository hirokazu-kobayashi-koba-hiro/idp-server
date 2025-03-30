package org.idp.server.core.handler.sharedsignal;

import java.util.logging.Logger;
import org.idp.server.core.hook.*;
import org.idp.server.core.sharedsignal.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantRepository;

public class EventHandler {

  TenantRepository tenantRepository;
  EventRepository eventRepository;
  AuthenticationHooks authenticationHooks;
  HookConfigurationQueryRepository hookConfigurationQueryRepository;
  SharedSignalFrameworkConfigurationRepository sharedSignalFrameworkConfigurationRepository;
  SharedSignalEventGateway eventGateway;
  Logger log = Logger.getLogger(EventHandler.class.getName());

  public EventHandler(
      TenantRepository tenantRepository,
      EventRepository eventRepository,
      AuthenticationHooks authenticationHooks,
      HookConfigurationQueryRepository hookConfigurationQueryRepository,
      SharedSignalFrameworkConfigurationRepository sharedSignalFrameworkConfigurationRepository,
      SharedSignalEventGateway eventGateway) {
    this.tenantRepository = tenantRepository;
    this.eventRepository = eventRepository;
    this.authenticationHooks = authenticationHooks;
    this.hookConfigurationQueryRepository = hookConfigurationQueryRepository;
    this.sharedSignalFrameworkConfigurationRepository =
        sharedSignalFrameworkConfigurationRepository;
    this.eventGateway = eventGateway;
  }

  public void handle(Event event) {
    eventRepository.register(event);

    Tenant tenant = tenantRepository.get(event.tenantIdentifier());

    HookTriggerType hookTriggerType = HookTriggerType.of(event.type().value());
    if (hookTriggerType.isDefined()) {

      HookConfigurations hookConfigurations =
          hookConfigurationQueryRepository.find(tenant, hookTriggerType);

      if (hookConfigurations.exists()) {
        HookRequest hookRequest = new HookRequest(event.toMap());

        hookConfigurations.forEach(
            hookConfiguration -> {
              log.info(
                  String.format(
                      "hook execution trigger: %s, type: %s tenant: %s client: %s user: %s, ",
                      hookConfiguration.triggerType().name(),
                      hookConfiguration.hookType().name(),
                      event.tenantIdentifierValue(),
                      event.clientId().value(),
                      event.user().id()));
              HookExecutor hookExecutor = authenticationHooks.get(hookConfiguration.hookType());
              hookExecutor.execute(
                  tenant, HookTriggerType.POST_LOGIN, hookRequest, hookConfiguration);
            });
      }
    }

    SecurityEventTokenEntityConvertor convertor = new SecurityEventTokenEntityConvertor(event);
    SecurityEventTokenEntity securityEventTokenEntity = convertor.convert();

    SharedSignalFrameworkConfiguration configuration =
        sharedSignalFrameworkConfigurationRepository.find(event.tokenIssuer().value());

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
