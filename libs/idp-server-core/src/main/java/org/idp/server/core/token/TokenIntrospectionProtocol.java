package org.idp.server.core.token;

import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;

public interface TokenIntrospectionProtocol {

  TokenIntrospectionResponse inspect(TokenIntrospectionRequest request);
}
