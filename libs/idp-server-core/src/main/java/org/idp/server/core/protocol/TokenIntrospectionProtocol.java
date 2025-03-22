package org.idp.server.core.protocol;

import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;

public interface TokenIntrospectionProtocol {

  TokenIntrospectionResponse inspect(TokenIntrospectionRequest request);
}
