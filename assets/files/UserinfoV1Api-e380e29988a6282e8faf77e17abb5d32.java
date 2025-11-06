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

package org.idp.server.adapters.springboot.application.restapi.userinfo;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.application.restapi.SecurityHeaderConfigurable;
import org.idp.server.core.openid.userinfo.UserinfoApi;
import org.idp.server.core.openid.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/v1/userinfo")
public class UserinfoV1Api implements ParameterTransformable, SecurityHeaderConfigurable {

  UserinfoApi userinfoApi;

  public UserinfoV1Api(IdpServerApplication idpServerApplication) {
    this.userinfoApi = idpServerApplication.userinfoApi();
  }

  @GetMapping
  public ResponseEntity<?> get(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserinfoRequestResponse response =
        userinfoApi.request(tenantId, authorizationHeader, clientCert, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setCacheControl("no-store, private");
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserinfoRequestResponse response =
        userinfoApi.request(tenantId, authorizationHeader, clientCert, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setCacheControl("no-store, private");
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
