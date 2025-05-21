package org.idp.server.authentication.interactors.fidouaf;

import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

public class FidoUafExecutors {

  Map<FidoUafExecutorType, FidoUafExecutor> executors;

  public FidoUafExecutors(Map<FidoUafExecutorType, FidoUafExecutor> executors) {
    this.executors = executors;
  }

  public FidoUafExecutor get(FidoUafExecutorType type) {
    FidoUafExecutor executor = executors.get(type);

    if (executor == null) {
      throw new UnSupportedException("No fido-uaf executor found for type " + type.name());
    }

    return executor;
  }
}
