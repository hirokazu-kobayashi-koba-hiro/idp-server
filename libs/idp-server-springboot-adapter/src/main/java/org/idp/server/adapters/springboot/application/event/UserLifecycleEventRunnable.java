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
import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;

public class UserLifecycleEventRunnable implements Runnable {

  UserLifecycleEvent userLifecycleEvent;
  Consumer<UserLifecycleEvent> handler;

  public UserLifecycleEventRunnable(
      UserLifecycleEvent userLifecycleEvent, Consumer<UserLifecycleEvent> handler) {
    this.userLifecycleEvent = userLifecycleEvent;
    this.handler = handler;
  }

  public UserLifecycleEvent getEvent() {
    return userLifecycleEvent;
  }

  @Override
  public void run() {
    handler.accept(userLifecycleEvent);
  }
}
