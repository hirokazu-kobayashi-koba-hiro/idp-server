package org.idp.server.core.protocol;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationResponse;

public class TokenRevocationProtocolImpl implements TokenRevocationProtocol {

  TokenRevocationHandler handler;
  Logger log = Logger.getLogger(TokenRevocationProtocolImpl.class.getName());

  public TokenRevocationProtocolImpl(TokenRevocationHandler handler) {
    this.handler = handler;
  }

  public TokenRevocationResponse revoke(TokenRevocationRequest request) {
    try {
      return handler.handle(request);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new TokenRevocationResponse(TokenRevocationRequestStatus.SERVER_ERROR, Map.of());
    }
  }
}
