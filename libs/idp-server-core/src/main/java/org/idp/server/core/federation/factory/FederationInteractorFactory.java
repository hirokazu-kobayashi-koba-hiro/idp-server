package org.idp.server.core.federation.factory;

import org.idp.server.core.federation.FederationInteractor;
import org.idp.server.core.federation.FederationType;

public interface FederationInteractorFactory {

  FederationType type();

  FederationInteractor create(FederationDependencyContainer container);
}
