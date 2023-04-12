package org.idp.server.type.oauth;

import org.idp.server.basic.http.BasicAuth;

public class ClientSecretBasic {
  BasicAuth basicAuth;

  public ClientSecretBasic() {}

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
