package org.idp.server.core.oidc.token;

import org.idp.server.core.oidc.token.handler.token.io.TokenRequest;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;

public interface TokenProtocol {

  AuthorizationProvider authorizationProtocolProvider();

  TokenRequestResponse request(TokenRequest tokenRequest);

  TokenIntrospectionResponse inspect(TokenIntrospectionRequest request);

  TokenRevocationResponse revoke(TokenRevocationRequest request);
}
