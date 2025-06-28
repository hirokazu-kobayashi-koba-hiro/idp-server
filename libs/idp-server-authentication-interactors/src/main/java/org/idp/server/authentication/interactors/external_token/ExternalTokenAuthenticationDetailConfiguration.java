/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.authentication.interactors.external_token;

import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.identity.mapper.UserinfoMappingRule;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonReadable;

public class ExternalTokenAuthenticationDetailConfiguration implements JsonReadable {
  String url;
  String method;
  Map<String, String> headers;
  List<HttpRequestMappingRule> headerMappingRules;
  List<String> dynamicBodyKeys;
  Map<String, Object> staticBody;
  List<UserinfoMappingRule> userinfoMappingRules;

  public ExternalTokenAuthenticationDetailConfiguration() {}

  public HttpRequestUrl httpRequestUrl() {
    return new HttpRequestUrl(url);
  }

  public HttpMethod httpMethod() {
    return HttpMethod.valueOf(method);
  }

  public HttpRequestStaticHeaders httpRequestHeaders() {
    return new HttpRequestStaticHeaders(headers);
  }

  public HttpRequestHeaderMappingRules httpRequestHeaderMappingRules() {
    return new HttpRequestHeaderMappingRules(headerMappingRules);
  }

  public HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys() {
    return new HttpRequestDynamicBodyKeys(dynamicBodyKeys);
  }

  public HttpRequestStaticBody httpRequestStaticBody() {
    return new HttpRequestStaticBody(staticBody);
  }

  public List<UserinfoMappingRule> userinfoMappingRules() {
    return userinfoMappingRules;
  }
}
