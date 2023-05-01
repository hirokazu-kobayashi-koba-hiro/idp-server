package org.idp.server.oauth.repository;

import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;

/** AuthorizationRequestRepository */
public interface AuthorizationRequestRepository {
  void register(AuthorizationRequest authorizationRequest);

  AuthorizationRequest get(AuthorizationRequestIdentifier authorizationRequestIdentifier);

  AuthorizationRequest find(AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
