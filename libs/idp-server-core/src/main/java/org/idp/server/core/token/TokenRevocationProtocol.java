package org.idp.server.core.token;

import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationResponse;

public interface TokenRevocationProtocol {

  TokenRevocationResponse revoke(TokenRevocationRequest request);
}
