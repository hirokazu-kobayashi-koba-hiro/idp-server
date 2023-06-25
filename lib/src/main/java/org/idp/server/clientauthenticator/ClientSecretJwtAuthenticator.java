package org.idp.server.clientauthenticator;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.oauth.clientcredentials.ClientAssertionJwt;
import org.idp.server.oauth.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.mtls.ClientCertification;
import org.idp.server.type.oauth.ClientAuthenticationType;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecret;

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
