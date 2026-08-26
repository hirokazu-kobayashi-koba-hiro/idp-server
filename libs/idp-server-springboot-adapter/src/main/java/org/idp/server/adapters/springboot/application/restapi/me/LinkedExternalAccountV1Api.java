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

package org.idp.server.adapters.springboot.application.restapi.me;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.account_linking.AccountLinkingApi;
import org.idp.server.account_linking.AccountLinkingState;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.io.AccountLinkingResponse;
import org.idp.server.account_linking.io.AccountLinkingStartRequest;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.application.restapi.model.ResourceOwnerPrincipal;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Bearer authenticated half of account linking.
 *
 * <p>The browser legs of the flow live outside {@code /me}, which {@code
 * ProtectedResourceApiFilter} protects on the strength of a Bearer token that a redirect from an
 * external IdP cannot carry.
 */
@RestController
@RequestMapping("/{tenant-id}/v1/me/linked-external-accounts")
public class LinkedExternalAccountV1Api implements ParameterTransformable {

  AccountLinkingApi accountLinkingApi;

  public LinkedExternalAccountV1Api(IdpServerApplication idpServerApplication) {
    this.accountLinkingApi = idpServerApplication.accountLinkingApi();
  }

  @PostMapping("/link/{provider}")
  public ResponseEntity<?> startLink(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("provider") String provider,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AccountLinkingResponse response =
        accountLinkingApi.startLink(
            tenantIdentifier,
            user,
            oAuthToken,
            new ExternalIdpProvider(provider),
            new AccountLinkingStartRequest(requestBody),
            requestAttributes);

    return toResponseEntity(response);
  }

  @PostMapping("/complete")
  public ResponseEntity<?> complete(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    RequestAttributes requestAttributes = transform(httpServletRequest);
    String state = requestBody == null ? null : String.valueOf(requestBody.get("state"));

    AccountLinkingResponse response =
        accountLinkingApi.complete(
            tenantIdentifier, user, oAuthToken, new AccountLinkingState(state), requestAttributes);

    return toResponseEntity(response);
  }

  @GetMapping
  public ResponseEntity<?> findList(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AccountLinkingResponse response =
        accountLinkingApi.findList(tenantIdentifier, user, requestAttributes);

    return toResponseEntity(response);
  }

  private ResponseEntity<?> toResponseEntity(AccountLinkingResponse response) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
