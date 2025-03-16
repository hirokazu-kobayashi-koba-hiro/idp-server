package org.idp.server.adapters.springboot.event;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.function.EventFunction;
import org.idp.server.core.sharedsignal.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EventListerService {

  Logger log = LoggerFactory.getLogger(EventListerService.class);
  EventFunction eventFunction;

  public EventListerService(IdpServerApplication idpServerApplication) {
    this.eventFunction = idpServerApplication.eventFunction();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEvent(Event event) {
    log.info("onEvent: {}", event.toMap());
    eventFunction.handle(event);
  }
}
