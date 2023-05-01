package org.idp.server.handler.oauth.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;

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
      throw new RuntimeException(
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
