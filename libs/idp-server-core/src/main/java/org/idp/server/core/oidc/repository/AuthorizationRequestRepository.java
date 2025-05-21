package org.idp.server.core.oidc.repository;

import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** AuthorizationRequestRepository */
public interface AuthorizationRequestRepository {
  void register(Tenant tenant, AuthorizationRequest authorizationRequest);

  AuthorizationRequest get(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);

  AuthorizationRequest find(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);

  void delete(Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
