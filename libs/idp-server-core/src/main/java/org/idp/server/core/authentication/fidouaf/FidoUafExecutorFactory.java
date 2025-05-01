package org.idp.server.core.authentication.fidouaf;

import org.idp.server.core.authentication.AuthenticationDependencyContainer;

public interface FidoUafExecutorFactory {

  FidoUafExecutor create(AuthenticationDependencyContainer container);
}
