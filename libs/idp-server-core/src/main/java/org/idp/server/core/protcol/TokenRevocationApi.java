package org.idp.server.core.protcol;

import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationResponse;

public interface TokenRevocationApi {

  TokenRevocationResponse revoke(TokenRevocationRequest request);
}
