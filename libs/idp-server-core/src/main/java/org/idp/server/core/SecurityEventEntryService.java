package org.idp.server.core;

import org.idp.server.core.api.SecurityEventApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.sharedsignal.SecurityEventHandler;
import org.idp.server.core.sharedsignal.*;

@Transactional
public class SecurityEventEntryService implements SecurityEventApi {

  SecurityEventHandler securityEventHandler;

  public SecurityEventEntryService(SecurityEventHandler securityEventHandler) {
    this.securityEventHandler = securityEventHandler;
  }

  @Override
  public void handle(Event event) {
    try {

      securityEventHandler.handle(event);
    } catch (Exception e) {

      e.printStackTrace();
    }
  }
}
