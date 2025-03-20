package org.idp.server.core.ciba.service;

import java.util.Set;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.basic.jose.JoseHandler;
import org.idp.server.core.basic.jose.JoseInvalidException;
import org.idp.server.core.ciba.*;
import org.idp.server.core.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.RequestObjectPatternFactory;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.oauth.ClientSecretBasic;

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
              clientConfiguration.clientSecretValue());
      joseContext.verifySignature();
      CibaRequestPattern pattern = CibaRequestPattern.REQUEST_OBJECT;
      Set<String> filteredScopes =
          filterScopes(pattern, parameters, joseContext, clientConfiguration);
      CibaProfile profile = analyze(filteredScopes, serverConfiguration);

      BackchannelAuthenticationRequest backchannelAuthenticationRequest =
          requestObjectPatternFactory.create(
              profile,
              clientSecretBasic,
              parameters,
              joseContext,
              filteredScopes,
              serverConfiguration,
              clientConfiguration);

      return new CibaRequestContext(
          pattern,
          clientSecretBasic,
          parameters,
          new CibaRequestObjectParameters(joseContext.claims().payload()),
          joseContext,
          backchannelAuthenticationRequest,
          serverConfiguration,
          clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object", exception.getMessage(), exception);
    }
  }
}
