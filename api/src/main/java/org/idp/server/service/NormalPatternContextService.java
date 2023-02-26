package org.idp.server.service;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.AuthorizationProfileAnalyzable;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.oauth.request.OAuthRequestContext;
import org.idp.server.core.oauth.request.OAuthRequestContextService;
import org.idp.server.core.type.OAuthRequestParameters;

/** NormalPatternContextService */
public class NormalPatternContextService
    implements OAuthRequestContextService, AuthorizationProfileAnalyzable {

  @Override
  public OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    JoseContext joseContext = new JoseContext();
    AuthorizationProfile profile =
        analyze(parameters, joseContext, serverConfiguration, clientConfiguration);

    return new OAuthRequestContext(
        profile,
        OAuthRequestPattern.NORMAL,
        parameters,
        joseContext,
        serverConfiguration,
        clientConfiguration);
  }
}
