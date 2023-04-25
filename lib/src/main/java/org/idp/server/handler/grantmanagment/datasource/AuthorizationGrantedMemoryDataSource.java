package org.idp.server.handler.grantmanagment.datasource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.grantmangment.AuthorizationGranted;
import org.idp.server.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.grantmangment.AuthorizationGrantedRepository;

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
