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

package org.idp.server.adapters.springboot.application.restapi.metadata;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.authentication.interactors.AuthenticationExecutionResult;
import org.idp.server.authentication.interactors.fidouaf.AuthenticationMetaDataApi;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class FidoUafDiscoveryV1Api implements ParameterTransformable {

  AuthenticationMetaDataApi authenticationMetaDataApi;

  public FidoUafDiscoveryV1Api(IdpServerApplication idpServerApplication) {
    this.authenticationMetaDataApi = idpServerApplication.authenticationMetaDataApi();
  }

  @GetMapping("{tenant-id}/.well-known/fido/facets")
  public ResponseEntity<?> getConfiguration(
      @PathVariable("tenant-id") TenantIdentifier tenantId, HttpServletRequest request) {

    RequestAttributes requestAttributes = transform(request);

    AuthenticationExecutionResult result =
        authenticationMetaDataApi.getFidoUafFacets(tenantId, requestAttributes);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        result.contents(), httpHeaders, HttpStatus.valueOf(result.statusCode()));
  }
}
