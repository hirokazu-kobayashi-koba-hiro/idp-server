package org.idp.server.core.authentication.fidouaf;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public class FidoUafExecutorLoader {

  private static final Logger log = Logger.getLogger(FidoUafExecutorLoader.class.getName());

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
