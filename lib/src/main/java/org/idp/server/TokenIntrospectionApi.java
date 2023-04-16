package org.idp.server;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.handler.io.TokenIntrospectionRequest;
import org.idp.server.handler.io.TokenIntrospectionResponse;
import org.idp.server.handler.io.status.TokenIntrospectionRequestStatus;
import org.idp.server.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.tokenintrospection.exception.TokenInvalidException;

public class TokenIntrospectionApi {

  TokenIntrospectionHandler handler;
  Logger log = Logger.getLogger(TokenIntrospectionApi.class.getName());

  public TokenIntrospectionApi(TokenIntrospectionHandler handler) {
    this.handler = handler;
  }

  public TokenIntrospectionResponse inspect(TokenIntrospectionRequest request) {
    try {
      TokenIntrospectionResponse response = handler.handle(request);
      return response;
    } catch (TokenInvalidException exception) {
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(
          TokenIntrospectionRequestStatus.INVALID_TOKEN, contents);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(TokenIntrospectionRequestStatus.SERVER_ERROR, contents);
    }
  }
}
