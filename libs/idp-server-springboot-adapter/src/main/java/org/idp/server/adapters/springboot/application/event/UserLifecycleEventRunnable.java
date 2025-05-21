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
