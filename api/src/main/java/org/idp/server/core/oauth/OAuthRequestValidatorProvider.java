package org.idp.server.core.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.validator.NormalPatternContextCreator;
import org.idp.server.core.oauth.validator.OAuthRequestContextCreator;
import org.idp.server.core.oauth.validator.RequestObjectPatternContextCreator;
import org.idp.server.core.oauth.validator.RequestUriPatternContextCreator;

/** OAuthRequestValidatorProvider */
public class OAuthRequestValidatorProvider {

  static Map<OAuthRequestPattern, OAuthRequestContextCreator> map = new HashMap<>();

  static {
    map.put(OAuthRequestPattern.NORMAL, new NormalPatternContextCreator());
    map.put(OAuthRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextCreator());
    map.put(OAuthRequestPattern.REQUEST_URI, new RequestUriPatternContextCreator());
  }

  public OAuthRequestContextCreator provide(OAuthRequestPattern oAuthRequestPattern) {
    OAuthRequestContextCreator oAuthRequestContextCreator = map.get(oAuthRequestPattern);
    if (Objects.isNull(oAuthRequestContextCreator)) {
      throw new RuntimeException("not support request pattern");
    }
    return oAuthRequestContextCreator;
  }
}
