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
import java.net.URI;
import org.idp.server.account_linking.AccountLinkingApi;
import org.idp.server.account_linking.AccountLinkingState;
import org.idp.server.account_linking.io.AccountLinkingResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Browser legs of account linking.
 *
 * <p>Kept out of {@code /me} on purpose. Both endpoints are top level navigations without a Bearer
 * token, and putting them under a namespace that is otherwise Bearer protected invites the
 * assumption that the filter has already established who the caller is.
 */
@RestController
@RequestMapping("/{tenant-id}/v1/linking")
public class AccountLinkingV1Api implements ParameterTransformable {

  AccountLinkingApi accountLinkingApi;

  public AccountLinkingV1Api(IdpServerApplication idpServerApplication) {
    this.accountLinkingApi = idpServerApplication.accountLinkingApi();
  }

  @GetMapping("/start")
  public ResponseEntity<?> start(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam("state") String state,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AccountLinkingResponse response =
        accountLinkingApi.authorizeStart(
            tenantIdentifier, new AccountLinkingState(state), requestAttributes);

    return toResponseEntity(response);
  }

  @GetMapping("/callback/{provider}")
  public ResponseEntity<?> callback(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("provider") String provider,
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(value = "error", required = false) String error,
      @RequestParam(value = "error_description", required = false) String errorDescription,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AccountLinkingResponse response =
        accountLinkingApi.handleCallback(
            tenantIdentifier,
            new AccountLinkingState(state),
            code,
            error,
            errorDescription,
            requestAttributes);

    return toResponseEntity(response);
  }

  private ResponseEntity<?> toResponseEntity(AccountLinkingResponse response) {
    HttpHeaders httpHeaders = new HttpHeaders();

    if (response.isRedirect()) {
      httpHeaders.setLocation(URI.create(response.redirectUri()));
      return new ResponseEntity<>(httpHeaders, HttpStatus.FOUND);
    }

    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
