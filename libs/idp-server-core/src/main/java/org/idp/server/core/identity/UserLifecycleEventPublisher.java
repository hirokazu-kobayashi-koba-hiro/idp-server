package org.idp.server.core.identity;

public interface UserLifecycleEventPublisher {

  void publish(UserLifecycleEvent userLifecycleEvent);
}
