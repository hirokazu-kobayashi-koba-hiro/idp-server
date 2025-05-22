/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.oauth;

import org.idp.server.basic.http.BasicAuth;

public class ClientSecretBasic {
  BasicAuth basicAuth;

  public ClientSecretBasic() {
    this.basicAuth = new BasicAuth();
  }

  public ClientSecretBasic(BasicAuth basicAuth) {
    this.basicAuth = basicAuth;
  }

  public RequestedClientId clientId() {
    return new RequestedClientId(basicAuth.username());
  }

  public ClientSecret clientSecret() {
    return new ClientSecret(basicAuth.password());
  }

  public boolean exists() {
    return basicAuth.exists();
  }
}
