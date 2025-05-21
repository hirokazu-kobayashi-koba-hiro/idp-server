package org.idp.server.core.oidc.identity.event;

public interface UserLifecycleEventExecutor {

  UserLifecycleType lifecycleType();

  String name();

  boolean shouldExecute(UserLifecycleEvent userLifecycleEvent);

  UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent);
}
