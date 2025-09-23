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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.idp.server.core.openid.federation.sso.SsoCredentials;
import org.idp.server.core.openid.federation.sso.SsoCredentialsCommandRepository;
import org.idp.server.core.openid.federation.sso.SsoCredentialsQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class SsoCredentialsParameterResolver implements AdditionalRequestParameterResolver {

  SsoCredentialsQueryRepository ssoCredentialsQueryRepository;
  SsoCredentialsCommandRepository ssoCredentialsCommandRepository;
  HttpClient httpClient;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(SsoCredentialsParameterResolver.class);

  public SsoCredentialsParameterResolver(
      SsoCredentialsQueryRepository ssoCredentialsQueryRepository,
      SsoCredentialsCommandRepository ssoCredentialsCommandRepository) {
    this.ssoCredentialsQueryRepository = ssoCredentialsQueryRepository;
    this.ssoCredentialsCommandRepository = ssoCredentialsCommandRepository;
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public String type() {
    return "sso_credentials";
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

    try {

      SsoCredentialsParameterConfig ssoCredentialsParameterConfig =
          jsonConverter.read(
              additionalParameterConfig.details(), SsoCredentialsParameterConfig.class);
      SsoCredentials ssoCredentials = ssoCredentialsQueryRepository.find(tenant, user);

      Map<String, String> params = new HashMap<>();
      params.put("refresh_token", ssoCredentials.refreshToken());
      params.put("grant_type", "refresh_token");
      params.put("client_id", ssoCredentialsParameterConfig.clientId());

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(ssoCredentialsParameterConfig.tokenEndpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json");

      if (ssoCredentialsParameterConfig.isClientSecretBasic()) {
        builder.header("Authorization", ssoCredentialsParameterConfig.basicAuthenticationValue());
      } else {
        params.put("client_secret", ssoCredentialsParameterConfig.clientSecret());
      }
      HttpQueryParams httpQueryParams = new HttpQueryParams(params);
      builder.POST(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));

      HttpRequest httpRequest = builder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      String body = httpResponse.body();

      JsonNodeWrapper json = JsonNodeWrapper.fromString(body);
      String accessToken = json.getValueOrEmptyAsString("access_token");
      String refreshToken = json.getValueOrEmptyAsString("refresh_token");
      long expiresIn = Long.parseLong(json.getValueOrEmptyAsString("expires_in"));

      SsoCredentials updateWithToken =
          ssoCredentials.updateWithToken(accessToken, refreshToken, expiresIn);
      ssoCredentialsCommandRepository.register(tenant, user, updateWithToken);

      return AdditionalParameterResolveResult.success(json.toMap());
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error("SSO credentials parameter resolution failed: {}", e.getMessage(), e);

      IdentityVerificationErrorDetails errorDetails =
          IdentityVerificationErrorDetails.builder()
              .error("sso_credentials_error")
              .errorDescription("SSO credentials parameter resolution failed: " + e.getMessage())
              .addErrorDetail("phase", "pre_hook")
              .addErrorDetail("component", "sso_credentials_resolver")
              .addErrorDetail("error_type", "NETWORK_ERROR")
              .addErrorDetail("retryable", true)
              .build();

      ErrorHandlingStrategy strategy = additionalParameterConfig.errorHandlingStrategy();

      if (strategy == ErrorHandlingStrategy.FAIL_FAST) {
        return AdditionalParameterResolveResult.failFastError(errorDetails);
      } else {
        Map<String, Object> fallbackData =
            Map.of(
                "status_code", 500,
                "error", "server_error",
                "error_description", "SSO credentials unavailable");
        return AdditionalParameterResolveResult.resilientError(errorDetails, fallbackData);
      }
    }
  }
}
