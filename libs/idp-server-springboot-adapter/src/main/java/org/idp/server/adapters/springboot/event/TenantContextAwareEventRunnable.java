package org.idp.server.adapters.springboot.event;

import java.util.function.Consumer;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.tenant.TenantContext;

public class TenantContextAwareEventRunnable implements Runnable {

  SecurityEvent securityEvent;
  Consumer<SecurityEvent> handler;

  public TenantContextAwareEventRunnable(SecurityEvent securityEvent, Consumer<SecurityEvent> handler) {
    this.securityEvent = securityEvent;
    this.handler = handler;
  }

  public SecurityEvent getEvent() {
    return securityEvent;
  }

  @Override
  public void run() {
    try {

      TenantContext.set(securityEvent.tenantIdentifier());
      handler.accept(securityEvent);

    } finally {
      TenantContext.clear();
    }
  }
}
