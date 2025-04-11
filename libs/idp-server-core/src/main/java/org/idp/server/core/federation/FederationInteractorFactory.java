package org.idp.server.core.federation;

public interface FederationInteractorFactory {

  FederationType type();

  FederationInteractor create(FederationDependencyContainer container);
}
