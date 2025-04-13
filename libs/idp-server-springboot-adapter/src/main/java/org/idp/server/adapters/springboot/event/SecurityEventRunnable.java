package org.idp.server.adapters.springboot.event;

import java.util.function.Consumer;
import org.idp.server.core.security.SecurityEvent;

public class SecurityEventRunnable implements Runnable {

  SecurityEvent securityEvent;
  Consumer<SecurityEvent> handler;

  public SecurityEventRunnable(SecurityEvent securityEvent, Consumer<SecurityEvent> handler) {
    this.securityEvent = securityEvent;
    this.handler = handler;
  }

  public SecurityEvent getEvent() {
    return securityEvent;
  }

  @Override
  public void run() {
    handler.accept(securityEvent);
  }
}
