package org.idp.server.core.clientauthenticator;

import org.idp.server.core.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.oauth.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.oauth.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.mtls.ClientCertification;
import org.idp.server.core.type.oauth.ClientAuthenticationType;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.ClientSecret;
import org.idp.server.core.type.oauth.ClientSecretBasic;

/**
 * client secret basic
 *
 * <p>Clients in possession of a client password MAY use the HTTP Basic authentication scheme as
 * defined in [RFC2617] to authenticate with the authorization server. The client identifier is
 * encoded using the "application/x-www-form-urlencoded" encoding algorithm per Appendix B, and the
 * encoded value is used as the username; the client password is encoded using the same algorithm
 * and used as the password. The authorization server MUST support the HTTP Basic authentication
 * scheme for authenticating clients that were issued a client password.
 *
 * <p>For example (with extra line breaks for display purposes only):
 *
 * <p>Authorization: Basic czZCaGRSa3F0Mzo3RmpmcDBaQnIxS3REUmJuZlZkbUl3
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-2.3.1">2.3.1. Client Password</a>
 */
class ClientSecretBasicAuthenticator implements ClientAuthenticator {

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientSecretBasic(context);
    throwExceptionIfUnMatchClientSecret(context);
    ClientId clientId = context.clientConfiguration().clientId();
    ClientSecret clientSecret = context.clientSecretBasic().clientSecret();
    return new ClientCredentials(
        clientId,
        ClientAuthenticationType.client_secret_basic,
        clientSecret,
        new ClientAuthenticationPublicKey(),
        new ClientAssertionJwt(),
        new ClientCertification());
  }

  void throwExceptionIfUnMatchClientSecret(BackchannelRequestContext context) {
    ClientSecretBasic clientSecretBasic = context.clientSecretBasic();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!clientConfiguration.matchClientSecret(clientSecretBasic.clientSecret().value())) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_basic, but request client_secret does not match client_secret");
    }
  }

  void throwExceptionIfNotContainsClientSecretBasic(BackchannelRequestContext context) {
    if (!context.hasClientSecretBasic()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_basic, but request does not contains client_secret_basic");
    }
  }
}
