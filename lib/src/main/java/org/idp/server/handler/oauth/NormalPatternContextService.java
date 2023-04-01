package org.idp.server.handler.oauth;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.*;
import org.idp.server.core.oauth.factory.NormalRequestFactory;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.OAuthRequestContextService;
import org.idp.server.core.type.OAuthRequestParameters;

/** NormalPatternContextService */
public class NormalPatternContextService
    implements OAuthRequestContextService, AuthorizationProfileAnalyzable {

  NormalRequestFactory normalRequestFactory = new NormalRequestFactory();

  @Override
  public OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    JoseContext joseContext = new JoseContext();
    AuthorizationProfile profile =
        analyze(parameters, joseContext, serverConfiguration, clientConfiguration);
    AuthorizationRequest authorizationRequest =
        normalRequestFactory.create(
            profile, parameters, joseContext, serverConfiguration, clientConfiguration);

    return new OAuthRequestContext(
        OAuthRequestPattern.NORMAL,
        parameters,
        joseContext,
        authorizationRequest,
        serverConfiguration,
        clientConfiguration);
  }
}
