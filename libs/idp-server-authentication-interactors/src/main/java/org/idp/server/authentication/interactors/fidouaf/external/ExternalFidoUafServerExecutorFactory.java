/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.fidouaf.external;

import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutor;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutorFactory;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;

public class ExternalFidoUafServerExecutorFactory implements FidoUafExecutorFactory {

  @Override
  public FidoUafExecutor create(AuthenticationDependencyContainer container) {
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new ExternalFidoUafServerExecutor(configurationQueryRepository);
  }
}
