package org.idp.server.core.oauth.validator;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.type.OAuthRequestParameters;

/** OAuthRequestContextCreator */
public interface OAuthRequestContextCreator {

  void create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration configuration);
}