package org.idp.server.core.oidc.validator;

import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.request.OAuthRequestParameters;

public class RequestObjectValidator {

  OAuthRequestParameters parameters;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public RequestObjectValidator(OAuthRequestParameters parameters, ServerConfiguration serverConfiguration, ClientConfiguration clientConfiguration) {
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public void validate() {}
}
