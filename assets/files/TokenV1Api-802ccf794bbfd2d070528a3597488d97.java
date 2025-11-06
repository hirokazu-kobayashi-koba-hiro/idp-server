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

package org.idp.server.adapters.springboot.application.restapi.token;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.application.restapi.SecurityHeaderConfigurable;
import org.idp.server.core.openid.token.TokenApi;
import org.idp.server.core.openid.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/v1/tokens")
public class TokenV1Api implements ParameterTransformable, SecurityHeaderConfigurable {

  TokenApi tokenApi;

  public TokenV1Api(IdpServerApplication idpServerApplication) {
    this.tokenApi = idpServerApplication.tokenAPi();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @RequestBody(required = false) MultiValueMap<String, String> body,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> request = transform(body);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenRequestResponse response =
        tokenApi.request(
            tenantIdentifier, request, authorizationHeader, clientCert, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setAll(response.responseHeaders());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/introspection")
  public ResponseEntity<?> inspect(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @RequestBody(required = false) MultiValueMap<String, String> body,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> request = transform(body);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenIntrospectionResponse response =
        tokenApi.inspect(
            tenantIdentifier, request, authorizationHeader, clientCert, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setAll(response.responseHeaders());
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/introspection-extensions")
  public ResponseEntity<?> inspectWithVerification(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @RequestBody(required = false) MultiValueMap<String, String> body,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> request = transform(body);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenIntrospectionResponse response =
        tokenApi.inspectWithVerification(
            tenantIdentifier, request, authorizationHeader, clientCert, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setAll(response.responseHeaders());
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/revocation")
  public ResponseEntity<?> revoke(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @RequestBody(required = false) MultiValueMap<String, String> body,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> request = transform(body);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenRevocationResponse response =
        tokenApi.revoke(
            tenantIdentifier, request, authorizationHeader, clientCert, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setAll(response.responseHeaders());
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
