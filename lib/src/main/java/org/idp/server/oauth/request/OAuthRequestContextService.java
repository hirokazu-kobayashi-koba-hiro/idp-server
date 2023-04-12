package org.idp.server.oauth.request;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.type.OAuthRequestParameters;

/** OAuthRequestContextService */
public interface OAuthRequestContextService {

  OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
