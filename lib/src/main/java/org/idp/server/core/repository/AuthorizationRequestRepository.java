package org.idp.server.core.repository;

import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;

/** AuthorizationRequestRepository */
public interface AuthorizationRequestRepository {
  void register(AuthorizationRequest authorizationRequest);

  AuthorizationRequest get(AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
