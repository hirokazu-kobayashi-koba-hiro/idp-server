package org.idp.server.api;

import org.idp.server.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationResponse;

public interface TokenRevocationApi {

  TokenRevocationResponse revoke(TokenRevocationRequest request);
}
