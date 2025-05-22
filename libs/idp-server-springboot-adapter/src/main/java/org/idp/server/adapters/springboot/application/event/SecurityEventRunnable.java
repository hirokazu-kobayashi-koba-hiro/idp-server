/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.event;

import java.util.function.Consumer;
import org.idp.server.platform.security.SecurityEvent;

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
