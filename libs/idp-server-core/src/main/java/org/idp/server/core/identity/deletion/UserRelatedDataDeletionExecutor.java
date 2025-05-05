package org.idp.server.core.identity.deletion;

import org.idp.server.core.identity.UserLifecycleEvent;

public interface UserRelatedDataDeletionExecutor {

  String name();

  boolean shouldExecute(UserLifecycleEvent userLifecycleEvent);

  UserDeletionResult execute(UserLifecycleEvent userLifecycleEvent);
}
