package org.idp.server.clientauthenticator;

import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.type.oauth.ClientSecret;

/**
 * client secret post
 *
 * <p>Alternatively, the authorization server MAY support including the client credentials in the
 * request-body using the following parameters:
 *
 * <p>client_id REQUIRED. The client identifier issued to the client during the registration process
 * described by Section 2.2.
 *
 * <p>client_secret REQUIRED. The client secret. The client MAY omit the parameter if the client
 * secret is an empty string.
 */
class ClientSecretPostAuthenticator implements ClientAuthenticator {

  @Override
  public void authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientSecretPost(context);
    throwExceptionIfUnMatchClientSecret(context);
  }

  void throwExceptionIfUnMatchClientSecret(BackchannelRequestContext context) {
    BackchannelRequestParameters parameters = context.parameters();
    ClientSecret clientSecret = parameters.clientSecret();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!clientConfiguration.matchClientSecret(clientSecret.value())) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_post, but request client_secret does not match client_secret");
    }
  }

  void throwExceptionIfNotContainsClientSecretPost(BackchannelRequestContext context) {
    BackchannelRequestParameters parameters = context.parameters();
    if (!parameters.hasClientSecret()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_post, but request does not contains client_secret_post");
    }
  }
}
