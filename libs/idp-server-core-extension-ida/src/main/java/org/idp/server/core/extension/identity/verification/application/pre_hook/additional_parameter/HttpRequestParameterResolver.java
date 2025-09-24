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
import org.idp.server.core.extension.identity.verification.configuration.ErrorHandlingStrategy;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationErrorDetails;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.type.RequestAttributes;

public class HttpRequestParameterResolver implements AdditionalRequestParameterResolver {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(HttpRequestParameterResolver.class);

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
  public AdditionalParameterResolveResult resolve(
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

    // Check for errors and handle based on configured strategy
    if (httpRequestResult.isClientError() || httpRequestResult.isServerError()) {
      log.warn(
          "HTTP request failed in pre_hook phase with status: {}", httpRequestResult.statusCode());

      IdentityVerificationErrorDetails errorDetails = createErrorDetails(httpRequestResult);
      ErrorHandlingStrategy strategy = additionalParameterConfig.errorHandlingStrategy();

      if (strategy == ErrorHandlingStrategy.FAIL_FAST) {
        return AdditionalParameterResolveResult.failFastError(errorDetails);
      } else {
        Map<String, Object> fallbackData = createFallbackData();
        return AdditionalParameterResolveResult.resilientError(errorDetails, fallbackData);
      }
    }

    // Success case
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("response_status", httpRequestResult.statusCode());
    parameters.put("response_headers", httpRequestResult.headers());
    parameters.put("response_body", httpRequestResult.body().toMap());

    return AdditionalParameterResolveResult.success(parameters);
  }

  private HttpRequestBaseParams resolveBaseParams(
      Tenant tenant,
      User user,
      IdentityVerificationApplication application,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("user", user.toMap());
    if (application != null && application.exists()) {
      parameters.put("application", application.toMap());
    }
    parameters.put("request_body", request.toMap());
    parameters.put("request_attributes", requestAttributes.toMap());

    return new HttpRequestBaseParams(parameters);
  }

  private IdentityVerificationErrorDetails createErrorDetails(HttpRequestResult httpRequestResult) {
    Map<String, Object> errorBody = httpRequestResult.body().toMap();

    IdentityVerificationErrorDetails.Builder builder =
        IdentityVerificationErrorDetails.builder()
            .error("external_service_error")
            .errorDescription(
                "External HTTP request failed with status: " + httpRequestResult.statusCode())
            .addErrorDetail("phase", "pre_hook")
            .addErrorDetail("component", "http_request_resolver")
            .addErrorDetail("status_code", httpRequestResult.statusCode());

    // Store original error response as-is since external service error formats are unpredictable
    for (Map.Entry<String, Object> entry : errorBody.entrySet()) {
      builder.addErrorDetail(entry.getKey(), entry.getValue());
    }

    return builder.build();
  }

  private Map<String, Object> createFallbackData() {
    // Provide default values for expected response structure in resilient mode
    Map<String, Object> fallbackData = new HashMap<>();
    fallbackData.put("response_status", 0);
    fallbackData.put("response_headers", new HashMap<>());
    fallbackData.put("response_body", new HashMap<>());
    return fallbackData;
  }
}
