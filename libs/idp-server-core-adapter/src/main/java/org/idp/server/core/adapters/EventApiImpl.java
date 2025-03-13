package org.idp.server.core.adapters;

import org.idp.server.core.EventApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.sharedsignal.EventHandler;
import org.idp.server.core.sharedsignal.*;

@Transactional
public class EventApiImpl implements EventApi {

  EventHandler eventHandler;

  public EventApiImpl(EventHandler eventHandler) {
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
