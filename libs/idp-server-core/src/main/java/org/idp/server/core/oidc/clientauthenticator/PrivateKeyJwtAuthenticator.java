package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
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

class PrivateKeyJwtAuthenticator
    implements ClientAuthenticator, ClientAuthenticationJwtValidatable {

  JoseHandler joseHandler = new JoseHandler();

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.private_key_jwt;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientAssertion(context);
    JoseContext joseContext = parseOrThrowExceptionIfUnMatchClientAssertion(context);
    RequestedClientId requestedClientId = context.requestedClientId();
    ClientSecret clientSecret = new ClientSecret();
    ClientAuthenticationPublicKey clientAuthenticationPublicKey =
        new ClientAuthenticationPublicKey(joseContext.jsonWebKey());
    ClientAssertionJwt clientAssertionJwt = new ClientAssertionJwt(joseContext.jsonWebSignature());

    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.private_key_jwt,
        clientSecret,
        clientAuthenticationPublicKey,
        clientAssertionJwt,
        new ClientCertification());
  }

  void throwExceptionIfNotContainsClientAssertion(BackchannelRequestContext context) {
    BackchannelRequestParameters parameters = context.parameters();
    if (!parameters.hasClientAssertion()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_jwt, but request does not contains client_assertion");
    }
    if (!parameters.hasClientAssertionType()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_jwt, but request does not contains client_assertion_type");
    }
  }

  JoseContext parseOrThrowExceptionIfUnMatchClientAssertion(BackchannelRequestContext context) {
    try {
      BackchannelRequestParameters parameters = context.parameters();
      ClientConfiguration clientConfiguration = context.clientConfiguration();
      JoseContext joseContext =
          joseHandler.handle(
              parameters.clientAssertion().value(),
              clientConfiguration.jwks(),
              clientConfiguration.jwks(),
              clientConfiguration.clientSecretValue());
      joseContext.verifySignature();
      validate(joseContext, context);
      return joseContext;
    } catch (JoseInvalidException e) {
      throw new ClientUnAuthorizedException(e.getMessage());
    }
  }
}
