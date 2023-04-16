package org.idp.server.tokenintrospection;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.token.AccessTokenPayload;

public class TokenIntrospectionContentsCreator {

  public static Map<String, Object> createSuccessContents(AccessTokenPayload accessTokenPayload) {
    Map<String, Object> contents = new HashMap<>(accessTokenPayload.values());
    contents.put("active", true);
    return contents;
  }

  public static Map<String, Object> createFailureContents() {
    return Map.of("active", false);
  }
}
