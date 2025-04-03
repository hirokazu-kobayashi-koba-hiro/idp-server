package org.idp.server.core.api;

import org.idp.server.core.security.SecurityEvent;

public interface SecurityEventApi {
  void handle(SecurityEvent securityEvent);
}
