package org.idp.server.application.service.event;

import org.idp.server.core.sharedsignal.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EventListerService {

  Logger log = LoggerFactory.getLogger(EventListerService.class);

  public EventListerService() {}

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEvent(Event event) {}
}
