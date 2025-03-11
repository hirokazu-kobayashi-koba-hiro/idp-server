package org.idp.server.core.type.oauth;

import org.idp.server.core.basic.http.BasicAuth;

public class ClientSecretBasic {
  BasicAuth basicAuth;

  public ClientSecretBasic() {
    this.basicAuth = new BasicAuth();
  }

  public ClientSecretBasic(BasicAuth basicAuth) {
    this.basicAuth = basicAuth;
  }

  public ClientId clientId() {
    return new ClientId(basicAuth.username());
  }

  public ClientSecret clientSecret() {
    return new ClientSecret(basicAuth.password());
  }

  public boolean exists() {
    return basicAuth.exists();
  }
}
