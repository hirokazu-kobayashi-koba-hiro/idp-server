package org.idp.server.grantmangment;

public interface AuthorizationGrantedRepository {

  void register(AuthorizationGranted authorizationGranted);

  AuthorizationGranted get(AuthorizationGrantedIdentifier identifier);
}
