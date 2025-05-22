/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.base.authorizer;

public class ControlPlaneAuthorizer {

  public boolean authorize(String authorizationHeader) {
    return true;
  }

  public boolean isAuthorized(String authorizationHeader) {
    return authorize(authorizationHeader);
  }
}
