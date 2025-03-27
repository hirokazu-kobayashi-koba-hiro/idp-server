package org.idp.server.adapters.springboot.event;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.SecurityEventApi;
import org.idp.server.core.sharedsignal.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EventListerService {

  Logger log = LoggerFactory.getLogger(EventListerService.class);
  SecurityEventApi securityEventApi;

  public EventListerService(IdpServerApplication idpServerApplication) {
    this.securityEventApi = idpServerApplication.eventFunction();
  }

  @Async
  @EventListener
  public void onEvent(Event event) {
    log.info("onEvent: {}", event.toMap());
    securityEventApi.handle(event);
  }
}
