package org.idp.server.core.token.verifier;

import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.token.TokenRequestContext;

public interface AuthorizationCodeGrantVerifierInterface {

  void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials);
}
