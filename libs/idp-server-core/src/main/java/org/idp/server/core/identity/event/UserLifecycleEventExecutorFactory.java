package org.idp.server.core.identity.event;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public interface UserLifecycleEventExecutorFactory {

  UserLifecycleEventExecutor create(ApplicationComponentContainer applicationComponentContainer, AuthenticationDependencyContainer authenticationDependencyContainer);
}
