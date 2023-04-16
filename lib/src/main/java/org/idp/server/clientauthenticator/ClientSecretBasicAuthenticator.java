package org.idp.server.clientauthenticator;

import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.type.oauth.ClientSecretBasic;

public class ClientSecretBasicAuthenticator implements ClientAuthenticator {

  @Override
  public void authenticate(BackchannelRequestContext context) {
    throwIfNotContainsClientSecretBasic(context);
    throwIfUnMatchClientSecret(context);
  }

  void throwIfUnMatchClientSecret(BackchannelRequestContext context) {
    ClientSecretBasic clientSecretBasic = context.clientSecretBasic();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!clientConfiguration.matchClientSecret(clientSecretBasic.clientSecret().value())) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_basic, but request client_secret does not match client_secret");
    }
  }

  void throwIfNotContainsClientSecretBasic(BackchannelRequestContext context) {
    if (context.hasClientSecretBasic()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_basic, but request does not contains client_secret_basic");
    }
  }
}
