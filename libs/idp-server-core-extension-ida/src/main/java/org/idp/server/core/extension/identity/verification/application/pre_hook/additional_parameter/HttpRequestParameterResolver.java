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
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.type.RequestAttributes;

// TODO handle error
public class HttpRequestParameterResolver implements AdditionalRequestParameterResolver {

  OAuthAuthorizationResolvers authorizationResolvers;
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public HttpRequestParameterResolver(OAuthAuthorizationResolvers oAuthAuthorizationResolvers) {
    this.authorizationResolvers = oAuthAuthorizationResolvers;
    this.httpRequestExecutor =
        new HttpRequestExecutor(HttpClientFactory.defaultClient(), authorizationResolvers);
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
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfig additionalParameterConfig) {

    HttpRequestBaseParams baseParams =
        resolveBaseParams(tenant, user, currentApplication, request, requestAttributes);

    AdditionalParameterHttpRequestConfig configuration =
        jsonConverter.read(
            additionalParameterConfig.details(), AdditionalParameterHttpRequestConfig.class);

    HttpRequestResult httpRequestResult = httpRequestExecutor.execute(configuration, baseParams);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("response_status", httpRequestResult.statusCode());
    parameters.put("response_headers", httpRequestResult.headers());
    parameters.put("response_body", httpRequestResult.body().toMap());

    return parameters;
  }

  private HttpRequestBaseParams resolveBaseParams(
      Tenant tenant,
      User user,
      IdentityVerificationApplication application,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("user", user.toMap());
    parameters.put("application", application.toMap());
    parameters.put("request_body", request.toMap());
    parameters.put("request_attributes", requestAttributes.toMap());

    return new HttpRequestBaseParams(parameters);
  }
}
