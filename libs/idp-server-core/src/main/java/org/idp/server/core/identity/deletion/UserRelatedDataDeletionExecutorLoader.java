package org.idp.server.core.identity.deletion;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public class UserRelatedDataDeletionExecutorLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(UserRelatedDataDeletionExecutorLoader.class);

  public static UserRelatedDataDeletionExecutors load(
      ApplicationComponentContainer applicationComponentContainer,
      AuthenticationDependencyContainer authenticationDependencyContainer) {
    List<UserRelatedDataDeletionExecutor> executors = new ArrayList<>();
    ServiceLoader<UserRelatedDataDeletionExecutorFactory> loader =
        ServiceLoader.load(UserRelatedDataDeletionExecutorFactory.class);

    for (UserRelatedDataDeletionExecutorFactory factory : loader) {
      UserRelatedDataDeletionExecutor executor =
          factory.create(applicationComponentContainer, authenticationDependencyContainer);
      executors.add(executor);
      log.info("Registered Dynamic UserRelatedDataDeletionExecutor: " + executor.name());
    }

    return new UserRelatedDataDeletionExecutors(executors);
  }
}
