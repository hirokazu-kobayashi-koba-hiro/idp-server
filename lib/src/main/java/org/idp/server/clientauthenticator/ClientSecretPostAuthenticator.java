package org.idp.server.clientauthenticator;

import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.type.oauth.ClientSecret;

class ClientSecretPostAuthenticator implements ClientAuthenticator {

  @Override
  public void authenticate(BackchannelRequestContext context) {
    throwIfNotContainsClientSecretPost(context);
    throwIfUnMatchClientSecret(context);
  }

  void throwIfUnMatchClientSecret(BackchannelRequestContext context) {
    BackchannelRequestParameters parameters = context.parameters();
    ClientSecret clientSecret = parameters.clientSecret();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!clientConfiguration.matchClientSecret(clientSecret.value())) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_post, but request client_secret does not match client_secret");
    }
  }

  void throwIfNotContainsClientSecretPost(BackchannelRequestContext context) {
    BackchannelRequestParameters parameters = context.parameters();
    if (!parameters.hasClientSecret()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_post, but request does not contains client_secret_post");
    }
  }
}
