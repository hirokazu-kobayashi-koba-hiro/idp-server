package org.idp.server.core.identity.deletion;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public interface UserRelatedDataDeletionExecutorFactory {

  UserRelatedDataDeletionExecutor create(
      ApplicationComponentContainer applicationComponentContainer,
      AuthenticationDependencyContainer authenticationDependencyContainer);
}
