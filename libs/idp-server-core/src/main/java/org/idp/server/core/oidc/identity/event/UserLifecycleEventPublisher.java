package org.idp.server.core.oidc.identity.event;

public interface UserLifecycleEventPublisher {

  void publish(UserLifecycleEvent userLifecycleEvent);
}
