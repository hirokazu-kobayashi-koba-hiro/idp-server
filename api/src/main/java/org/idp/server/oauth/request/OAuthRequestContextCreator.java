package org.idp.server.oauth.request;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.OAuthRequestParameters;

/** OAuthRequestContextCreator */
public interface OAuthRequestContextCreator {

  OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
