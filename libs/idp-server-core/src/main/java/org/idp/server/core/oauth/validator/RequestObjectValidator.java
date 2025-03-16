package org.idp.server.core.oauth.validator;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.request.OAuthRequestParameters;

public class RequestObjectValidator {

  OAuthRequestParameters parameters;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public RequestObjectValidator(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public void validate() {}
}
