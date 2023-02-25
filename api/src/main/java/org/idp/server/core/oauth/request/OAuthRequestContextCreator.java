package org.idp.server.core.oauth.request;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.OAuthRequestParameters;

/** OAuthRequestContextCreator */
public interface OAuthRequestContextCreator {

  OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
