package org.idp.server.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.oauth.request.OAuthRequestContextService;
import org.idp.server.httpclient.RequestObjectHttpClient;

/** OAuthRequestContextServiceRegistry */
public class OAuthRequestContextServiceRegistry {

  static Map<OAuthRequestPattern, OAuthRequestContextService> map = new HashMap<>();

  static {
    map.put(OAuthRequestPattern.NORMAL, new NormalPatternContextService());
    map.put(OAuthRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextService());
    map.put(
        OAuthRequestPattern.REQUEST_URI,
        new RequestUriPatternContextService(new RequestObjectHttpClient()));
  }

  public OAuthRequestContextService get(OAuthRequestPattern oAuthRequestPattern) {
    OAuthRequestContextService oAuthRequestContextService = map.get(oAuthRequestPattern);
    if (Objects.isNull(oAuthRequestContextService)) {
      throw new RuntimeException("not support request pattern");
    }
    return oAuthRequestContextService;
  }
}
