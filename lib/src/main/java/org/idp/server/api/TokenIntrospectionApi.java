package org.idp.server.api;

import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionResponse;

public interface TokenIntrospectionApi {

  TokenIntrospectionResponse inspect(TokenIntrospectionRequest request);
}
