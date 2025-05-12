package org.idp.server.control_plane.base.authorizer;

public class ControlPlaneAuthorizer {

  public boolean authorize(String authorizationHeader) {
    return true;
  }

  public boolean isAuthorized(String authorizationHeader) {
    return authorize(authorizationHeader);
  }
}
