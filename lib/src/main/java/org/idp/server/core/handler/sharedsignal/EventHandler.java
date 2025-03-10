package org.idp.server.core.handler.sharedsignal;

import java.util.logging.Logger;
import org.idp.server.core.sharedsignal.*;

public class EventHandler {

  EventRepository eventRepository;
  SharedSignalFrameworkConfigurationRepository sharedSignalFrameworkConfigurationRepository;
  SharedSignalEventGateway eventGateway;
  Logger log = Logger.getLogger(EventHandler.class.getName());

  public EventHandler(
      EventRepository eventRepository,
      SharedSignalFrameworkConfigurationRepository sharedSignalFrameworkConfigurationRepository,
      SharedSignalEventGateway eventGateway) {
    this.eventRepository = eventRepository;
    this.sharedSignalFrameworkConfigurationRepository =
        sharedSignalFrameworkConfigurationRepository;
    this.eventGateway = eventGateway;
  }

  public void handle(Event event) {
    eventRepository.register(event);

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
