package org.idp.server.core.oidc.response;

import org.idp.server.basic.type.oauth.RedirectUri;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.request.AuthorizationRequest;

public interface RedirectUriDecidable {

  default RedirectUri decideRedirectUri(
      AuthorizationRequest authorizationRequest, ClientConfiguration clientConfiguration) {
    if (authorizationRequest.hasRedirectUri()) {
      return authorizationRequest.redirectUri();
    }
    return clientConfiguration.getFirstRedirectUri();
  }
}
