package org.idp.server.core;

import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;

public interface TokenIntrospectionApi {

  TokenIntrospectionResponse inspect(TokenIntrospectionRequest request);
}
