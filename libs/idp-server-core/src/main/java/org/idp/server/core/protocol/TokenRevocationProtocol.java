package org.idp.server.core.protocol;

import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationResponse;

public interface TokenRevocationProtocol {

  TokenRevocationResponse revoke(TokenRevocationRequest request);
}
