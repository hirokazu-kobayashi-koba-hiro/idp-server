package org.idp.server.oauth.request;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.OAuthRequestParameters;

/** RequestObjectPatternValidator */
public class RequestObjectPatternContextCreator implements OAuthRequestContextCreator {

  @Override
  public OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    return new OAuthRequestContext();
  }
}
