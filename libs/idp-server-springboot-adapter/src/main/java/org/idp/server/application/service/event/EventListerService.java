package org.idp.server.application.service.event;

import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.EventApi;
import org.idp.server.core.sharedsignal.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EventListerService {

  Logger log = LoggerFactory.getLogger(EventListerService.class);
  EventApi eventApi;

  public EventListerService(IdpServerApplication idpServerApplication) {
    this.eventApi = idpServerApplication.eventApi();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEvent(Event event) {
    log.info("onEvent: {}", event.toMap());
    eventApi.handle(event);
  }
}
