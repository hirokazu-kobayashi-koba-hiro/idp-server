package org.idp.server.authentication.interactors.legacy;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.http.*;
import org.idp.server.basic.json.JsonReadable;

public class LegacyIdServiceAuthenticationDetailConfiguration implements JsonReadable {
  String url;
  String method;
  Map<String, String> headers;
  List<String> dynamicBodyKeys;
  Map<String, Object> staticBody;
  List<UserInfoMappingRule> userinfoMappingRules;

  public LegacyIdServiceAuthenticationDetailConfiguration() {}

  public HttpRequestUrl httpRequestUrl() {
    return new HttpRequestUrl(url);
  }

  public HttpMethod httpMethod() {
    return HttpMethod.valueOf(method);
  }

  public HttpRequestHeaders httpRequestHeaders() {
    return new HttpRequestHeaders(headers);
  }

  public HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys() {
    return new HttpRequestDynamicBodyKeys(dynamicBodyKeys);
  }

  public HttpRequestStaticBody httpRequestStaticBody() {
    return new HttpRequestStaticBody(staticBody);
  }

  public List<UserInfoMappingRule> userinfoMappingRules() {
    return userinfoMappingRules;
  }
}
