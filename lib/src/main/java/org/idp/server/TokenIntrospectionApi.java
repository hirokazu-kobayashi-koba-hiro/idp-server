package org.idp.server;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionResponse;
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
      return handler.handle(request);
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
