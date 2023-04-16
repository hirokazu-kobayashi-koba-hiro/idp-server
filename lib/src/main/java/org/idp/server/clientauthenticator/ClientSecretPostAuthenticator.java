package org.idp.server.clientauthenticator;

public class ClientSecretPostAuthenticator implements ClientAuthenticator {

  @Override
  public void authenticate(BackchannelRequestContext context) {
    throwIfNotContainsClientSecretPost(context);
    throwIfUnMatchClientSecret(context);
  }

  void throwIfUnMatchClientSecret(BackchannelRequestContext context) {
    //    ClientSecret clientSecret = context.clientSecretWithParams();
    //    ClientConfiguration clientConfiguration = context.clientConfiguration();
    //    if (!clientConfiguration.matchClientSecret(clientSecret.value())) {
    //      throw new ClientUnAuthorizedException(
    //          "client authentication type is client_secret_post, but request client_secret does
    // not match client_secret");
    //    }
  }

  void throwIfNotContainsClientSecretPost(BackchannelRequestContext context) {
    //    if (!context.hasClientSecretWithParams()) {
    //      throw new ClientUnAuthorizedException(
    //          "client authentication type is client_secret_post, but request does not contains
    // client_secret_post");
    //    }
  }
}
