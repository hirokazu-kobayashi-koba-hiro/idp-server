package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.security.*;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.core.security.handler.SecurityEventHandler;

@Transactional
public class SecurityEventEntryService implements SecurityEventApi {

  SecurityEventHandler securityEventHandler;

  public SecurityEventEntryService(SecurityEventHandler securityEventHandler) {
    this.securityEventHandler = securityEventHandler;
  }

  @Override
  public void handle(SecurityEvent securityEvent) {
    try {

      securityEventHandler.handle(securityEvent);
    } catch (Exception e) {

      e.printStackTrace();
    }
  }
}
