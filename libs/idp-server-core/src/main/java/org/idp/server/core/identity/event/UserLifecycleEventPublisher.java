package org.idp.server.core.identity.event;

public interface UserLifecycleEventPublisher {

  void publish(UserLifecycleEvent userLifecycleEvent);
}
