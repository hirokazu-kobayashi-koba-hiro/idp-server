package org.idp.server.core.adapters.datasource.grantmanagment;

import org.idp.server.core.grantmangment.AuthorizationGranted;
import org.idp.server.core.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AuthorizationGrantedMemoryDataSource implements AuthorizationGrantedRepository {

  Map<AuthorizationGrantedIdentifier, AuthorizationGranted> values = new HashMap<>();

  @Override
  public void register(AuthorizationGranted authorizationGranted) {
    values.put(authorizationGranted.identifier(), authorizationGranted);
  }

  @Override
  public AuthorizationGranted get(AuthorizationGrantedIdentifier identifier) {
    AuthorizationGranted authorizationGranted = values.get(identifier);
    if (Objects.isNull(authorizationGranted)) {
      throw new RuntimeException(
          String.format("not found authorization granted (%s)", identifier.value()));
    }
    return authorizationGranted;
  }
}
