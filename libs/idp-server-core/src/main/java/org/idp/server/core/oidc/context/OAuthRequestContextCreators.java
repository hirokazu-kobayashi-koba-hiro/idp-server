package org.idp.server.core.oidc.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.exception.UnSupportedException;
import org.idp.server.core.oidc.OAuthRequestPattern;
import org.idp.server.core.oidc.gateway.RequestObjectGateway;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;

public class OAuthRequestContextCreators {
  Map<OAuthRequestPattern, OAuthRequestContextCreator> values;

  public OAuthRequestContextCreators(
      RequestObjectGateway requestObjectGateway,
      AuthorizationRequestRepository authorizationRequestRepository) {
    values = new HashMap<>();
    values.put(OAuthRequestPattern.NORMAL, new NormalPatternContextCreator());
    values.put(OAuthRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextCreator());
    values.put(
        OAuthRequestPattern.REQUEST_URI, new RequestUriPatternContextCreator(requestObjectGateway));
    values.put(
        OAuthRequestPattern.PUSHED_REQUEST_URI,
        new PushedRequestUriPatternContextCreator(authorizationRequestRepository));
  }

  public OAuthRequestContextCreator get(OAuthRequestPattern pattern) {
    OAuthRequestContextCreator oAuthRequestContextCreator = values.get(pattern);
    if (Objects.isNull(oAuthRequestContextCreator)) {
      throw new UnSupportedException(
          String.format("not support request pattern (%s)", pattern.name()));
    }
    return oAuthRequestContextCreator;
  }
}
