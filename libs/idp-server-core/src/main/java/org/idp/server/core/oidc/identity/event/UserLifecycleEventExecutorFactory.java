package org.idp.server.core.oidc.identity.event;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentContainer;

public interface UserLifecycleEventExecutorFactory {

  UserLifecycleEventExecutor create(
      ApplicationComponentContainer applicationComponentContainer,
      AuthenticationDependencyContainer authenticationDependencyContainer);
}
