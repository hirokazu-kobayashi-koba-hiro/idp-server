package org.idp.server.core.token.verifier;

import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.token.TokenRequestContext;

public interface AuthorizationCodeGrantVerifierInterface {

  void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials);
}
