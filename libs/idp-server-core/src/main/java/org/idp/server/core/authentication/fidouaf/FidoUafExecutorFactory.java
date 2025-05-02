package org.idp.server.core.authentication.fidouaf;

import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public interface FidoUafExecutorFactory {

  FidoUafExecutor create(AuthenticationDependencyContainer container);
}
