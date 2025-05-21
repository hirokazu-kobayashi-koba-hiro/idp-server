package org.idp.server.authentication.interactors.fidouaf;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;

public interface FidoUafExecutorFactory {

  FidoUafExecutor create(AuthenticationDependencyContainer container);
}
