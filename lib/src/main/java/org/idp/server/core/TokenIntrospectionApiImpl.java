package org.idp.server.core;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.api.TokenIntrospectionApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.tokenintrospection.exception.TokenInvalidException;

@Transactional
public class TokenIntrospectionApiImpl implements TokenIntrospectionApi {

  TokenIntrospectionHandler handler;
  Logger log = Logger.getLogger(TokenIntrospectionApiImpl.class.getName());

  public TokenIntrospectionApiImpl(TokenIntrospectionHandler handler) {
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
