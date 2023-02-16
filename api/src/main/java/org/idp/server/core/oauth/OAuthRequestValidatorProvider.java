package org.idp.server.core.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.validator.NormalPatternValidator;
import org.idp.server.core.oauth.validator.OAuthRequestValidator;
import org.idp.server.core.oauth.validator.RequestObjectPatternValidator;
import org.idp.server.core.oauth.validator.RequestUriPatternValidator;

/** OAuthRequestValidatorProvider */
public class OAuthRequestValidatorProvider {

  static Map<OAuthRequestPattern, OAuthRequestValidator> map = new HashMap<>();

  static {
    map.put(OAuthRequestPattern.NORMAL, new NormalPatternValidator());
    map.put(OAuthRequestPattern.REQUEST_OBJECT, new RequestObjectPatternValidator());
    map.put(OAuthRequestPattern.REQUEST_URI, new RequestUriPatternValidator());
  }

  public OAuthRequestValidator provide(OAuthRequestPattern oAuthRequestPattern) {
    OAuthRequestValidator oAuthRequestValidator = map.get(oAuthRequestPattern);
    if (Objects.isNull(oAuthRequestValidator)) {
      throw new RuntimeException("not support request pattern");
    }
    return oAuthRequestValidator;
  }
}
