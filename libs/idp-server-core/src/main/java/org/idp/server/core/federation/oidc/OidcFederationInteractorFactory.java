package org.idp.server.core.federation.oidc;

import org.idp.server.core.federation.*;

public class OidcFederationInteractorFactory implements FederationInteractorFactory {

  @Override
  public FederationType type() {
    return new FederationType("oidc");
  }

  @Override
  public FederationInteractor create(FederationDependencyContainer container) {
    OidcSsoExecutors oidcSsoExecutors = container.resolve(OidcSsoExecutors.class);
    FederationConfigurationQueryRepository configurationQueryRepository = container.resolve(FederationConfigurationQueryRepository.class);
    SsoSessionCommandRepository sessionCommandRepository = container.resolve(SsoSessionCommandRepository.class);
    SsoSessionQueryRepository sessionQueryRepository = container.resolve(SsoSessionQueryRepository.class);
    return new OidcFederationInteractor(oidcSsoExecutors, configurationQueryRepository, sessionCommandRepository, sessionQueryRepository);
  }
}
