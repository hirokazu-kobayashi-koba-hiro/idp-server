package org.idp.server.core;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.api.TokenRevocationApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationResponse;

@Transactional
public class TokenRevocationApiImpl implements TokenRevocationApi {

  TokenRevocationHandler handler;
  Logger log = Logger.getLogger(TokenRevocationApiImpl.class.getName());

  public TokenRevocationApiImpl(TokenRevocationHandler handler) {
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
