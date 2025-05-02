package org.idp.server.core.authentication.fidouaf;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public class FidoUafExecutorLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(FidoUafExecutorLoader.class);

  public static FidoUafExecutors load(AuthenticationDependencyContainer container) {
    Map<FidoUafExecutorType, FidoUafExecutor> executors = new HashMap<>();
    ServiceLoader<FidoUafExecutorFactory> loader = ServiceLoader.load(FidoUafExecutorFactory.class);

    for (FidoUafExecutorFactory factory : loader) {
      FidoUafExecutor fidoUafExecutor = factory.create(container);
      executors.put(fidoUafExecutor.type(), fidoUafExecutor);
      log.info(
          String.format("Dynamic Registered FidoUafExecutor %s", fidoUafExecutor.type().name()));
    }

    return new FidoUafExecutors(executors);
  }
}
