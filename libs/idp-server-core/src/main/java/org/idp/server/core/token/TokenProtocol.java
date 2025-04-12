package org.idp.server.core.token;

import org.idp.server.core.basic.dependency.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.token.handler.token.io.TokenRequest;
import org.idp.server.core.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationResponse;

public interface TokenProtocol {

  AuthorizationProtocolProvider authorizationProtocolProvider();

  TokenRequestResponse request(TokenRequest tokenRequest);

  TokenIntrospectionResponse inspect(TokenIntrospectionRequest request);

  TokenRevocationResponse revoke(TokenRevocationRequest request);
}
