package org.idp.server.core.oidc.identity.event;

import java.util.*;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;

public class UserLifecycleEventExecutorLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(UserLifecycleEventExecutorLoader.class);

  public static UserLifecycleEventExecutorsMap load(
      ApplicationComponentContainer applicationComponentContainer,
      AuthenticationDependencyContainer authenticationDependencyContainer) {
    Map<UserLifecycleType, List<UserLifecycleEventExecutor>> tempMap = new HashMap<>();
    ServiceLoader<UserLifecycleEventExecutorFactory> loader =
        ServiceLoader.load(UserLifecycleEventExecutorFactory.class);

    for (UserLifecycleEventExecutorFactory factory : loader) {
      UserLifecycleEventExecutor executor =
          factory.create(applicationComponentContainer, authenticationDependencyContainer);

      UserLifecycleType lifecycleType = executor.lifecycleType();
      tempMap.computeIfAbsent(lifecycleType, k -> new ArrayList<>()).add(executor);

      log.info(
          "Registered UserLifecycleEventExecutor: "
              + executor.name()
              + " for type "
              + lifecycleType);
    }

    Map<UserLifecycleType, UserLifecycleEventExecutors> resultMap = new HashMap<>();
    for (Map.Entry<UserLifecycleType, List<UserLifecycleEventExecutor>> entry :
        tempMap.entrySet()) {
      resultMap.put(entry.getKey(), new UserLifecycleEventExecutors(entry.getValue()));
    }

    return new UserLifecycleEventExecutorsMap(resultMap);
  }
}
