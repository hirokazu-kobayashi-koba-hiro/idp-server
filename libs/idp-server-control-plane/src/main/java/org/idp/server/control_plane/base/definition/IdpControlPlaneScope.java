package org.idp.server.control_plane.base.definition;

import org.idp.server.platform.exception.UnSupportedException;

public enum IdpControlPlaneScope {
  management;

  public static IdpControlPlaneScope of(String value) {
    for (IdpControlPlaneScope scope : IdpControlPlaneScope.values()) {
      if (scope.name().equalsIgnoreCase(value)) {
        return scope;
      }
    }
    throw new UnSupportedException("Scope " + value + " is not supported");
  }
}
