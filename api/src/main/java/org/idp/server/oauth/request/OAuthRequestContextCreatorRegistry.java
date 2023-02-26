package org.idp.server.oauth.request;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.oauth.OAuthRequestPattern;

/** OAuthRequestContextCreatorRegistry */
public class OAuthRequestContextCreatorRegistry {

  static Map<OAuthRequestPattern, OAuthRequestContextCreator> map = new HashMap<>();

  static {
    map.put(OAuthRequestPattern.NORMAL, new NormalPatternContextCreator());
    map.put(OAuthRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextCreator());
    map.put(OAuthRequestPattern.REQUEST_URI, new RequestUriPatternContextCreator());
  }

  public OAuthRequestContextCreator get(OAuthRequestPattern oAuthRequestPattern) {
    OAuthRequestContextCreator oAuthRequestContextCreator = map.get(oAuthRequestPattern);
    if (Objects.isNull(oAuthRequestContextCreator)) {
      throw new RuntimeException("not support request pattern");
    }
    return oAuthRequestContextCreator;
  }
}
