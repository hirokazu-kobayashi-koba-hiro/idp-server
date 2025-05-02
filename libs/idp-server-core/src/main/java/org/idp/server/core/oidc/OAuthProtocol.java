package org.idp.server.core.oidc;

import org.idp.server.basic.dependency.protocol.AuthorizationProtocolProvider;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.io.*;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;

public interface OAuthProtocol {

  AuthorizationProtocolProvider authorizationProtocolProvider();

  OAuthRequestResponse request(OAuthRequest oAuthRequest);

  OAuthViewDataResponse getViewData(OAuthViewDataRequest request);

  AuthorizationRequest get(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);

  OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request);

  OAuthDenyResponse deny(OAuthDenyRequest request);

  OAuthLogoutResponse logout(OAuthLogoutRequest oAuthLogoutRequest);
}
