package org.idp.server.core.oidc.federation.sso.oidc;

import org.idp.server.core.oidc.federation.*;
import org.idp.server.core.oidc.federation.plugin.FederationDependencyContainer;
import org.idp.server.core.oidc.federation.plugin.FederationInteractorFactory;
import org.idp.server.core.oidc.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.oidc.federation.sso.SsoSessionCommandRepository;
import org.idp.server.core.oidc.federation.sso.SsoSessionQueryRepository;

public class OidcFederationInteractorFactory implements FederationInteractorFactory {

  @Override
  public FederationType type() {
    return StandardSupportedFederationType.OIDC.toFederationType();
  }

  @Override
  public FederationInteractor create(FederationDependencyContainer container) {
    OidcSsoExecutors oidcSsoExecutors = container.resolve(OidcSsoExecutors.class);
    FederationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(FederationConfigurationQueryRepository.class);
    SsoSessionCommandRepository sessionCommandRepository =
        container.resolve(SsoSessionCommandRepository.class);
    SsoSessionQueryRepository sessionQueryRepository =
        container.resolve(SsoSessionQueryRepository.class);
    return new OidcFederationInteractor(
        oidcSsoExecutors,
        configurationQueryRepository,
        sessionCommandRepository,
        sessionQueryRepository);
  }
}
