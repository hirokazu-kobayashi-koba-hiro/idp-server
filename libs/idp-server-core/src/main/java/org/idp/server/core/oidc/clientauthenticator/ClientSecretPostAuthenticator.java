package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.basic.type.oauth.ClientSecret;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.oidc.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.oidc.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.oidc.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.mtls.ClientCertification;

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
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.client_secret_post;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientSecretPost(context);
    throwExceptionIfUnMatchClientSecret(context);
    RequestedClientId requestedClientId = context.requestedClientId();
    ClientSecret clientSecret = context.parameters().clientSecret();
    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.client_secret_post,
        clientSecret,
        new ClientAuthenticationPublicKey(),
        new ClientAssertionJwt(),
        new ClientCertification());
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
