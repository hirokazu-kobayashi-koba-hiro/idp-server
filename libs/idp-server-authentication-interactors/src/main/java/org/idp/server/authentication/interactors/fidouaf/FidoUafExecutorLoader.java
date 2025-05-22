/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.fidouaf;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;

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
