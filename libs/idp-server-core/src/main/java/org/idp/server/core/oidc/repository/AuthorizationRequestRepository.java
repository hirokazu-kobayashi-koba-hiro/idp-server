package org.idp.server.core.oidc.repository;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;

/** AuthorizationRequestRepository */
public interface AuthorizationRequestRepository {
  void register(Tenant tenant, AuthorizationRequest authorizationRequest);

  AuthorizationRequest get(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);

  AuthorizationRequest find(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);

  void delete(Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
