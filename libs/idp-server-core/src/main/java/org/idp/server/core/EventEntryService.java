package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.api.EventApi;
import org.idp.server.core.handler.sharedsignal.EventHandler;
import org.idp.server.core.sharedsignal.*;

@Transactional
public class EventEntryService implements EventApi {

  EventHandler eventHandler;

  public EventEntryService(EventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  @Override
  public void handle(Event event) {
    try {

      eventHandler.handle(event);
    } catch (Exception e) {

      e.printStackTrace();
    }
  }
}
