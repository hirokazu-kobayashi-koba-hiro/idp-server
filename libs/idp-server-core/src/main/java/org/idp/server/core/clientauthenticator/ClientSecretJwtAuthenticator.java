package org.idp.server.core.clientauthenticator;

import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.basic.jose.JoseHandler;
import org.idp.server.core.basic.jose.JoseInvalidException;
import org.idp.server.core.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.oauth.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.oauth.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.mtls.ClientCertification;
import org.idp.server.core.type.oauth.ClientAuthenticationType;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.ClientSecret;

class ClientSecretJwtAuthenticator
    implements ClientAuthenticator, ClientAuthenticationJwtValidatable {

  JoseHandler joseHandler = new JoseHandler();

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientAssertion(context);
    JoseContext joseContext = parseOrThrowExceptionIfUnMatchClientAssertion(context);
    ClientId clientId = context.clientConfiguration().clientId();
    ClientSecret clientSecret = new ClientSecret(context.clientConfiguration().clientSecretValue());
    ClientAssertionJwt clientAssertionJwt = new ClientAssertionJwt(joseContext.jsonWebSignature());
    return new ClientCredentials(
        clientId,
        ClientAuthenticationType.client_secret_jwt,
        clientSecret,
        new ClientAuthenticationPublicKey(),
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
