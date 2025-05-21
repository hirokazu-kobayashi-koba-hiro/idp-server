package org.idp.server.core.oidc.federation.plugin;

import org.idp.server.core.oidc.federation.FederationInteractor;
import org.idp.server.core.oidc.federation.FederationType;

public interface FederationInteractorFactory {

  FederationType type();

  FederationInteractor create(FederationDependencyContainer container);
}
