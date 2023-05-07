package org.idp.server.oauth.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.oauth.gateway.RequestObjectGateway;

public class OAuthRequestContextServices {
  Map<OAuthRequestPattern, OAuthRequestContextService> values;

  public OAuthRequestContextServices(RequestObjectGateway requestObjectGateway) {
    values = new HashMap<>();
    values.put(OAuthRequestPattern.NORMAL, new NormalPatternContextService());
    values.put(OAuthRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextService());
    values.put(
        OAuthRequestPattern.REQUEST_URI, new RequestUriPatternContextService(requestObjectGateway));
  }

  public OAuthRequestContextService get(OAuthRequestPattern pattern) {
    OAuthRequestContextService oAuthRequestContextService = values.get(pattern);
    if (Objects.isNull(oAuthRequestContextService)) {
      throw new RuntimeException("not support request pattern");
    }
    return oAuthRequestContextService;
  }
}
