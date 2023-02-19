package org.idp.server.core.oauth.validator;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.type.OAuthRequestParameters;

/** RequestObjectPatternValidator */
public class RequestObjectPatternValidator implements OAuthRequestValidator {

  @Override
  public void validate(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration configuration) {}
}
