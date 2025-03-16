package org.idp.server.core.grantmangment;

public interface AuthorizationGrantedRepository {

  void register(AuthorizationGranted authorizationGranted);

  AuthorizationGranted get(AuthorizationGrantedIdentifier identifier);
}
