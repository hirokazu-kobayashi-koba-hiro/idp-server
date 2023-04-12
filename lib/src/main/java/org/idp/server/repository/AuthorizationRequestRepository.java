package org.idp.server.repository;

import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;

/** AuthorizationRequestRepository */
public interface AuthorizationRequestRepository {
  void register(AuthorizationRequest authorizationRequest);

  AuthorizationRequest get(AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
