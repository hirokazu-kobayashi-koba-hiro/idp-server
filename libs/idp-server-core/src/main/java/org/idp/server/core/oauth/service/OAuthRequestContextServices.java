package org.idp.server.core.oauth.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.oauth.gateway.RequestObjectGateway;
import org.idp.server.core.type.exception.UnSupportedException;

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
      throw new UnSupportedException(
          String.format("not support request pattern (%s)", pattern.name()));
    }
    return oAuthRequestContextService;
  }
}
