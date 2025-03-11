package org.idp.server.core.handler.oauth.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.exception.OAuthException;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;

/** AuthorizationRequestMemoryDataSource */
public class AuthorizationRequestMemoryDataSource implements AuthorizationRequestRepository {

  Map<AuthorizationRequestIdentifier, AuthorizationRequest> map;

  public AuthorizationRequestMemoryDataSource() {
    this.map = new HashMap<>();
  }

  @Override
  public void register(AuthorizationRequest authorizationRequest) {
    map.put(authorizationRequest.identifier(), authorizationRequest);
  }

  @Override
  public AuthorizationRequest get(AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequest authorizationRequest = map.get(authorizationRequestIdentifier);
    if (Objects.isNull(authorizationRequest)) {
      throw new OAuthException(
          "invalid_request",
          String.format("not found oauth request (%s)", authorizationRequestIdentifier.value()));
    }
    return authorizationRequest;
  }

  @Override
  public AuthorizationRequest find(AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequest authorizationRequest = map.get(authorizationRequestIdentifier);
    if (Objects.isNull(authorizationRequest)) {
      return new AuthorizationRequest();
    }
    return authorizationRequest;
  }
}
