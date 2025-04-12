package org.idp.server.core.token;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.token.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.token.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.token.tokenintrospection.exception.TokenInvalidException;

public class TokenIntrospectionProtocolImpl implements TokenIntrospectionProtocol {

  TokenIntrospectionHandler handler;
  Logger log = Logger.getLogger(TokenIntrospectionProtocolImpl.class.getName());

  public TokenIntrospectionProtocolImpl(TokenIntrospectionHandler handler) {
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
