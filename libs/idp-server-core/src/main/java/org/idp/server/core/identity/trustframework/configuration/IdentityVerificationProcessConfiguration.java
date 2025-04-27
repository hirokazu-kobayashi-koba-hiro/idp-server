package org.idp.server.core.identity.trustframework.configuration;

import java.util.List;
import java.util.Map;
import org.idp.server.core.authentication.legacy.UserInfoMappingRule;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.basic.json.JsonReadable;

public class IdentityVerificationProcessConfiguration implements JsonReadable {
  String url;
  String method;
  Map<String, String> headers;
  List<String> dynamicBodyKeys;
  Map<String, Object> staticBody;
  List<UserInfoMappingRule> userinfoMappingRules;
  Map<String, Object> requestValidationSchema;
  Map<String, Object> responseValidationSchema;

  public IdentityVerificationProcessConfiguration() {}

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

  public Map<String, Object> requestValidationSchema() {
    return requestValidationSchema;
  }

  public Map<String, Object> responseValidationSchema() {
    return responseValidationSchema;
  }
}
