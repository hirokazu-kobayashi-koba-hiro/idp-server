package org.idp.server.core.authenticator;

import org.idp.server.core.authenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.token.TokenRequestContext;
import org.idp.server.core.type.oauth.ClientSecret;

public class ClientSecretPostAuthenticator implements ClientAuthenticator {

  @Override
  public void authenticate(TokenRequestContext tokenRequestContext) {
    throwIfNotContainsClientSecretPost(tokenRequestContext);
    throwIfUnMatchClientSecret(tokenRequestContext);
  }

  void throwIfUnMatchClientSecret(TokenRequestContext tokenRequestContext) {
    ClientSecret clientSecret = tokenRequestContext.clientSecretWithParams();
    if (!tokenRequestContext.matchClientSecret(clientSecret)) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_post, but request client_secret does not match client_secret");
    }
  }

  void throwIfNotContainsClientSecretPost(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.hasClientSecretWithParams()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_post, but request does not contains client_secret_post");
    }
  }
}
