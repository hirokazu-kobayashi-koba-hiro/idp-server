/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
