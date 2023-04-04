package org.idp.server.core.oauth.authenticator;

import org.idp.server.core.oauth.TokenRequestContext;
import org.idp.server.core.oauth.exception.ClientUnAuthorizedException;
import org.idp.server.core.type.ClientSecretBasic;

public class ClientSecretBasicAuthenticator implements ClientAuthenticator {

  @Override
  public void authenticate(TokenRequestContext tokenRequestContext) {
    throwIfNotContainsClientSecretBasic(tokenRequestContext);
    throwIfUnMatchClientSecret(tokenRequestContext);
  }

  void throwIfUnMatchClientSecret(TokenRequestContext tokenRequestContext) {
    ClientSecretBasic clientSecretBasic = tokenRequestContext.clientSecretBasic();
    if (!tokenRequestContext.matchClientSecret(clientSecretBasic.clientSecret())) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_basic, but request client_secret does not match client_secret");
    }
  }

  void throwIfNotContainsClientSecretBasic(TokenRequestContext tokenRequestContext) {
    if (tokenRequestContext.hasClientSecretBasic()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_basic, but request does not contains client_secret_basic");
    }
  }
}
