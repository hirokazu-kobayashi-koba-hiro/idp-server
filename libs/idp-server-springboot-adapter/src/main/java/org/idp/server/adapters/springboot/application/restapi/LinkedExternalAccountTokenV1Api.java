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

package org.idp.server.adapters.springboot.application.restapi;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.account_linking.AccountAlias;
import org.idp.server.account_linking.AccountLinkingApi;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/**
 * Hands a stored external IdP access token to an authenticated client.
 *
 * <p>Outside {@code /me}: the caller is the client, authenticated by its own credentials, and the
 * user is named by the {@code token} form parameter rather than a Bearer header.
 */
@RestController
@RequestMapping("/{tenant-id}/v1/linked-external-accounts")
public class LinkedExternalAccountTokenV1Api implements ParameterTransformable {

  AccountLinkingApi accountLinkingApi;

  public LinkedExternalAccountTokenV1Api(IdpServerApplication idpServerApplication) {
    this.accountLinkingApi = idpServerApplication.accountLinkingApi();
  }

  @PostMapping(value = "/{alias}/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<?> retrieveToken(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("alias") String alias,
      @RequestBody(required = false) MultiValueMap<String, String> body,
      HttpServletRequest httpServletRequest) {

    HttpRequestInputs inputs = transformInputs(body, httpServletRequest);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AccountLinkingResult result =
        accountLinkingApi.retrieveToken(
            tenantIdentifier, new AccountAlias(alias), inputs, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    httpHeaders.add("Cache-Control", "no-store");
    httpHeaders.add("Pragma", "no-cache");
    return new ResponseEntity<>(
        result.contents(), httpHeaders, HttpStatus.valueOf(result.statusCode()));
  }
}
