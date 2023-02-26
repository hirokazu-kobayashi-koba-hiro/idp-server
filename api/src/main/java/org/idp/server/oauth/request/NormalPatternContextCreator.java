package org.idp.server.oauth.request;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.OAuthRequestParameters;

/** NormalPatternValidator */
public class NormalPatternContextCreator implements OAuthRequestContextCreator {

  @Override
  public OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    return new OAuthRequestContext();
  }
}
