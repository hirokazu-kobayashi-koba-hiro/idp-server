package org.idp.server.core.oidc.federation;

import org.idp.server.core.oidc.federation.io.FederationCallbackRequest;
import org.idp.server.core.oidc.federation.io.FederationRequestResponse;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface FederationInteractor {

  FederationRequestResponse request(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider);

  FederationInteractionResult callback(
      Tenant tenant,
      FederationType federationType,
      SsoProvider ssoProvider,
      FederationCallbackRequest callbackRequest,
      UserQueryRepository userQueryRepository);
}
