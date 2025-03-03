package org.idp.server.core.oauth.response;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.oauth.RedirectUri;

public interface RedirectUriDecidable {

  default RedirectUri decideRedirectUri(
      AuthorizationRequest authorizationRequest, ClientConfiguration clientConfiguration) {
    if (authorizationRequest.hasRedirectUri()) {
      return authorizationRequest.redirectUri();
    }
    return clientConfiguration.getFirstRedirectUri();
  }
}
