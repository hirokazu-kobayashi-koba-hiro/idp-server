package org.idp.server.handler.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.OAuthRequestAnalyzer;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.oauth.request.OAuthRequestContextService;
import org.idp.server.core.type.OAuthRequestParameters;
import org.idp.server.httpclient.RequestObjectHttpClient;

/** OAuthRequestContextHandler */
public class OAuthRequestContextHandler {

  static Map<OAuthRequestPattern, OAuthRequestContextService> map = new HashMap<>();
  OAuthRequestAnalyzer requestAnalyzer = new OAuthRequestAnalyzer();

  static {
    map.put(OAuthRequestPattern.NORMAL, new NormalPatternContextService());
    map.put(OAuthRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextService());
    map.put(
        OAuthRequestPattern.REQUEST_URI,
        new RequestUriPatternContextService(new RequestObjectHttpClient()));
  }

  public OAuthRequestContext handle(OAuthRequestParameters parameters, ServerConfiguration serverConfiguration, ClientConfiguration clientConfiguration) {
    OAuthRequestPattern oAuthRequestPattern = requestAnalyzer.analyzePattern(parameters);
    OAuthRequestContextService oAuthRequestContextService = map.get(oAuthRequestPattern);
    if (Objects.isNull(oAuthRequestContextService)) {
      throw new RuntimeException("not support request pattern");
    }
    return oAuthRequestContextService.create(parameters, serverConfiguration, clientConfiguration);
  }
}
