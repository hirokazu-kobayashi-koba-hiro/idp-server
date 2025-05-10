package org.idp.server.core.token;

import org.idp.server.basic.dependency.protocol.AuthorizationProvider;
import org.idp.server.core.token.handler.token.io.TokenRequest;
import org.idp.server.core.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationResponse;

public interface TokenProtocol {

  AuthorizationProvider authorizationProtocolProvider();

  TokenRequestResponse request(TokenRequest tokenRequest);

  TokenIntrospectionResponse inspect(TokenIntrospectionRequest request);

  TokenRevocationResponse revoke(TokenRevocationRequest request);
}
