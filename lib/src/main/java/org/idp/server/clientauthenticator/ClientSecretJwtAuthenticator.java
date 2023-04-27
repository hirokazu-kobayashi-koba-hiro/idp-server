package org.idp.server.clientauthenticator;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;

class ClientSecretJwtAuthenticator
    implements ClientAuthenticator, ClientAuthenticationJwtValidatable {

  JoseHandler joseHandler = new JoseHandler();

  @Override
  public void authenticate(BackchannelRequestContext context) {
    throwIfNotContainsClientAssertion(context);
    throwIfUnMatchClientAssertion(context);
  }

  void throwIfNotContainsClientAssertion(BackchannelRequestContext context) {
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

  void throwIfUnMatchClientAssertion(BackchannelRequestContext context) {
    try {
      BackchannelRequestParameters parameters = context.parameters();
      ServerConfiguration serverConfiguration = context.serverConfiguration();
      ClientConfiguration clientConfiguration = context.clientConfiguration();
      JoseContext joseContext =
          joseHandler.handle(
              parameters.clientAssertion().value(),
              serverConfiguration.jwks(),
              clientConfiguration.jwks(),
              clientConfiguration.clientSecret());
      joseContext.verifySignature();
      validate(joseContext, context);
    } catch (JoseInvalidException e) {
      throw new ClientUnAuthorizedException(e.getMessage());
    }
  }
}
