package org.idp.server.core.identity.event;

import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentContainer;

public interface UserLifecycleEventExecutorFactory {

  UserLifecycleEventExecutor create(
      ApplicationComponentContainer applicationComponentContainer,
      AuthenticationDependencyContainer authenticationDependencyContainer);
}
