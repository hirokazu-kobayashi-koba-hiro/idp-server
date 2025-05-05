package org.idp.server.core.identity.event;

public interface UserLifecycleEventExecutor {

  UserLifecycleType lifecycleType();

  String name();

  boolean shouldExecute(UserLifecycleEvent userLifecycleEvent);

  UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent);
}
