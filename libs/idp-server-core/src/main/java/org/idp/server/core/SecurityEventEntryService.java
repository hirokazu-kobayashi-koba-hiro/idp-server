package org.idp.server.core;

import org.idp.server.core.api.SecurityEventApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.sharedsignal.EventHandler;
import org.idp.server.core.sharedsignal.*;

@Transactional
public class SecurityEventEntryService implements SecurityEventApi {

  EventHandler eventHandler;

  public SecurityEventEntryService(EventHandler eventHandler) {
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
