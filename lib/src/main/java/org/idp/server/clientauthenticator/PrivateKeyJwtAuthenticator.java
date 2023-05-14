package org.idp.server.clientauthenticator;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ClientConfiguration;

class PrivateKeyJwtAuthenticator
    implements ClientAuthenticator, ClientAuthenticationJwtValidatable {

  JoseHandler joseHandler = new JoseHandler();

  @Override
  public void authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientAssertion(context);
    throwExceptionIfUnMatchClientAssertion(context);
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

  void throwExceptionIfUnMatchClientAssertion(BackchannelRequestContext context) {
    try {
      BackchannelRequestParameters parameters = context.parameters();
      ClientConfiguration clientConfiguration = context.clientConfiguration();
      JoseContext joseContext =
          joseHandler.handle(
              parameters.clientAssertion().value(),
              clientConfiguration.jwks(),
              clientConfiguration.jwks(),
              clientConfiguration.clientSecret());
      joseContext.verifySignature();
      validate(joseContext, context);
    } catch (JoseInvalidException e) {
      throw new ClientUnAuthorizedException(e.getMessage());
    }
  }
}
