package org.idp.server.token.verifier;

import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.token.TokenRequestContext;

public interface AuthorizationCodeGrantVerifierInterface {

  void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials);
}
