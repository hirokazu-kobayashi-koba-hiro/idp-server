package org.idp.server.core.oauth.repository;

import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.tenant.Tenant;

/** AuthorizationRequestRepository */
public interface AuthorizationRequestRepository {
  void register(Tenant tenant, AuthorizationRequest authorizationRequest);

  AuthorizationRequest get(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);

  AuthorizationRequest find(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
