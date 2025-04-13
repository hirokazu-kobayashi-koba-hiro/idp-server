package org.idp.server.core.federation;

import org.idp.server.core.federation.io.FederationCallbackRequest;
import org.idp.server.core.federation.io.FederationRequestResponse;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.tenant.Tenant;

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
      UserRepository userRepository);
}
