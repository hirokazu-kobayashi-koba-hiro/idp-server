package org.idp.server;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationResponse;

public class TokenRevocationApi {

  TokenRevocationHandler handler;
  Logger log = Logger.getLogger(TokenRevocationApi.class.getName());

  public TokenRevocationApi(TokenRevocationHandler handler) {
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
