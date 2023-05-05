package org.idp.server.ciba.service;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.ciba.*;
import org.idp.server.ciba.exception.BackchannelAuthenticationBadRequest;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.ciba.request.RequestObjectPatternFactory;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.oauth.ClientSecretBasic;

/** RequestObjectPatternContextService */
public class RequestObjectPatternContextService
    implements CibaRequestContextService, CibaProfileAnalyzable {

  RequestObjectPatternFactory requestObjectPatternFactory = new RequestObjectPatternFactory();
  JoseHandler joseHandler = new JoseHandler();

  @Override
  public CibaRequestContext create(
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      JoseContext joseContext =
          joseHandler.handle(
              parameters.request().value(),
              clientConfiguration.jwks(),
              serverConfiguration.jwks(),
              clientConfiguration.clientSecret());
      joseContext.verifySignature();
      CibaRequestPattern pattern = CibaRequestPattern.REQUEST_OBJECT;
      Set<String> filteredScopes =
          filterScopes(pattern, parameters, joseContext, clientConfiguration);
      CibaProfile profile = analyze(filteredScopes, serverConfiguration);
      BackchannelAuthenticationRequest backchannelAuthenticationRequest =
          requestObjectPatternFactory.create(
              profile,
              parameters,
              joseContext,
              filteredScopes,
              serverConfiguration,
              clientConfiguration);
      return new CibaRequestContext(
          pattern,
          clientSecretBasic,
          parameters,
          joseContext,
          backchannelAuthenticationRequest,
          serverConfiguration,
          clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new BackchannelAuthenticationBadRequest(
          "invalid_request", exception.getMessage(), exception);
    }
  }
}
