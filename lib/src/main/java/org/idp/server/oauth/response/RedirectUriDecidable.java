package org.idp.server.oauth.response;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.oauth.RedirectUri;

public interface RedirectUriDecidable {

  default RedirectUri decideRedirectUri(
      AuthorizationRequest authorizationRequest, ClientConfiguration clientConfiguration) {
    if (authorizationRequest.hasRedirectUri()) {
      return authorizationRequest.redirectUri();
    }
    return clientConfiguration.getFirstRedirectUri();
  }
}
