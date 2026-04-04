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

import java.net.URI;
import java.net.http.HttpRequest;
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
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Resolves SSO credentials by refreshing the OAuth token from an external identity provider.
 *
 * <p>This resolver is used as a pre_hook additional_parameter in identity verification processes.
 * It retrieves the user's stored SSO credentials (refresh_token), exchanges them for a new
 * access_token at the configured token endpoint, and makes the token available to subsequent
 * processing steps.
 *
 * <h3>Error Classification</h3>
 *
 * <table>
 *   <tr><th>Scenario</th><th>error_type</th><th>retryable</th><th>HTTP Status</th></tr>
 *   <tr><td>Token endpoint returns 401/403</td><td>AUTHENTICATION_ERROR</td><td>false</td><td>400</td></tr>
 *   <tr><td>Token endpoint returns 5xx</td><td>SERVER_ERROR</td><td>true</td><td>400</td></tr>
 *   <tr><td>SSO credentials not found</td><td>UNEXPECTED_ERROR</td><td>false</td><td>400</td></tr>
 *   <tr><td>Connection failure / parse error</td><td>UNEXPECTED_ERROR</td><td>false</td><td>400</td></tr>
 * </table>
 *
 * <h3>Error Handling Strategy</h3>
 *
 * <p>The behavior on error is controlled by {@link ErrorHandlingStrategy}:
 *
 * <ul>
 *   <li>{@code FAIL_FAST} (default): Returns error immediately, halting the identity verification
 *       process.
 *   <li>{@code RESILIENT}: Continues processing with fallback data, allowing the identity
 *       verification to proceed without SSO credentials.
 * </ul>
 *
 * @see <a href="https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1456">#1456</a>
 */
public class SsoCredentialsParameterResolver implements AdditionalRequestParameterResolver {

  SsoCredentialsQueryRepository ssoCredentialsQueryRepository;
  SsoCredentialsCommandRepository ssoCredentialsCommandRepository;
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(SsoCredentialsParameterResolver.class);

  public SsoCredentialsParameterResolver(
      SsoCredentialsQueryRepository ssoCredentialsQueryRepository,
      SsoCredentialsCommandRepository ssoCredentialsCommandRepository,
      HttpRequestExecutor httpRequestExecutor) {
    this.ssoCredentialsQueryRepository = ssoCredentialsQueryRepository;
    this.ssoCredentialsCommandRepository = ssoCredentialsCommandRepository;
    this.httpRequestExecutor = httpRequestExecutor;
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
              .uri(URI.create(ssoCredentialsParameterConfig.tokenEndpoint()))
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
      HttpRequestResult httpRequestResult = httpRequestExecutor.execute(httpRequest);

      if (httpRequestResult.isClientError() || httpRequestResult.isServerError()) {
        int statusCode = httpRequestResult.statusCode();
        String responseBody =
            httpRequestResult.body() != null ? httpRequestResult.body().toString() : "";
        boolean isAuthenticationError = statusCode == 401 || statusCode == 403;
        String errorType = isAuthenticationError ? "AUTHENTICATION_ERROR" : "SERVER_ERROR";
        boolean retryable = !isAuthenticationError;

        log.warn(
            "SSO token refresh failed: status={}, authError={}, body={}",
            statusCode,
            isAuthenticationError,
            responseBody);

        IdentityVerificationErrorDetails errorDetails =
            IdentityVerificationErrorDetails.builder()
                .error("sso_credentials_error")
                .errorDescription(
                    "Token refresh failed: HTTP "
                        + statusCode
                        + (isAuthenticationError
                            ? " (refresh token may be invalid or revoked)"
                            : " (external provider error)"))
                .addErrorDetail("phase", "pre_hook")
                .addErrorDetail("component", "sso_credentials_resolver")
                .addErrorDetail("error_type", errorType)
                .addErrorDetail("retryable", retryable)
                .addErrorDetail("status_code", statusCode)
                .build();

        return handleError(
            additionalParameterConfig,
            errorDetails,
            Map.of(
                "status_code", statusCode,
                "error", isAuthenticationError ? "invalid_grant" : "server_error",
                "error_description",
                    isAuthenticationError
                        ? "SSO refresh token is invalid or revoked"
                        : "SSO credentials unavailable"));
      }

      JsonNodeWrapper json = JsonNodeWrapper.fromString(httpRequestResult.body().toString());
      String accessToken = json.getValueOrEmptyAsString("access_token");
      String refreshToken = json.getValueOrEmptyAsString("refresh_token");
      long expiresIn = parseExpiresIn(json.getValueOrEmptyAsString("expires_in"));

      SsoCredentials updateWithToken =
          ssoCredentials.updateWithToken(accessToken, refreshToken, expiresIn);
      ssoCredentialsCommandRepository.register(tenant, user, updateWithToken);

      log.debug("SSO credentials resolved successfully: user={}", user.userIdentifier().value());

      return AdditionalParameterResolveResult.success(json.toMap());
    } catch (Exception e) {
      log.error("SSO credentials parameter resolution failed: {}", e.getMessage(), e);

      IdentityVerificationErrorDetails errorDetails =
          IdentityVerificationErrorDetails.builder()
              .error("sso_credentials_error")
              .errorDescription("SSO credentials parameter resolution failed: " + e.getMessage())
              .addErrorDetail("phase", "pre_hook")
              .addErrorDetail("component", "sso_credentials_resolver")
              .addErrorDetail("error_type", "UNEXPECTED_ERROR")
              .addErrorDetail("retryable", false)
              .build();

      return handleError(
          additionalParameterConfig,
          errorDetails,
          Map.of(
              "status_code", 500,
              "error", "server_error",
              "error_description", "SSO credentials unavailable"));
    }
  }

  private AdditionalParameterResolveResult handleError(
      IdentityVerificationConfig config,
      IdentityVerificationErrorDetails errorDetails,
      Map<String, Object> fallbackData) {
    ErrorHandlingStrategy strategy = config.errorHandlingStrategy();
    if (strategy == ErrorHandlingStrategy.FAIL_FAST) {
      return AdditionalParameterResolveResult.failFastError(errorDetails);
    }
    return AdditionalParameterResolveResult.resilientError(errorDetails, fallbackData);
  }

  private long parseExpiresIn(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      log.warn("Invalid expires_in value: '{}', using default 300", value);
      return 300;
    }
  }
}
