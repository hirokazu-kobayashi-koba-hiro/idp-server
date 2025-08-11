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

package org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.type.RequestAttributes;

// TODO handle error
public class HttpRequestParameterResolver implements AdditionalRequestParameterResolver {

  OAuthAuthorizationResolvers authorizationResolvers;
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public HttpRequestParameterResolver() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String type() {
    return "http_request";
  }

  @Override
  public Map<String, Object> resolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfig additionalParameterConfig) {

    HttpRequestBaseParams baseParams = resolveBaseParams(request, requestAttributes);

    AdditionalParameterHttpRequestConfiguration configuration =
        jsonConverter.read(
            additionalParameterConfig.details(), AdditionalParameterHttpRequestConfiguration.class);

    HttpRequestResult executionResult = httpRequestExecutor.execute(configuration, baseParams);

    JsonNodeWrapper body = executionResult.body();
    Map<String, Object> parameters = new HashMap<>();
    String statusCodeName =
        configuration.optValueFromAdditionalParameterNames(
            "status_code", "additional_parameter_status_code");
    parameters.put(statusCodeName, executionResult.headers());
    String headerName =
        configuration.optValueFromAdditionalParameterNames(
            "header", "additional_parameter_http_header");
    parameters.put(headerName, executionResult.headers());
    String bodyName =
        configuration.optValueFromAdditionalParameterNames(
            "body", "additional_parameter_http_body");
    parameters.put(bodyName, body.toMap());

    return parameters;
  }

  private HttpRequestBaseParams resolveBaseParams(
      IdentityVerificationRequest request, RequestAttributes requestAttributes) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("request_body", request.toMap());
    parameters.put("request_attributes", requestAttributes.toMap());

    return new HttpRequestBaseParams(parameters);
  }
}
