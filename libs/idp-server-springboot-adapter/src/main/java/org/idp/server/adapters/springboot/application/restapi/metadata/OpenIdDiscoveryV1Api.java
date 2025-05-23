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

import org.idp.server.IdpServerApplication;
import org.idp.server.core.oidc.discovery.OidcMetaDataApi;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class OpenIdDiscoveryV1Api {

  OidcMetaDataApi oidcMetaDataApi;

  public OpenIdDiscoveryV1Api(IdpServerApplication idpServerApplication) {
    this.oidcMetaDataApi = idpServerApplication.oidcMetaDataApi();
  }

  @GetMapping("{tenant-id}/.well-known/openid-configuration")
  public ResponseEntity<?> getConfiguration(@PathVariable("tenant-id") TenantIdentifier tenantId) {

    ServerConfigurationRequestResponse response = oidcMetaDataApi.getConfiguration(tenantId);
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("{tenant-id}/v1/jwks")
  public ResponseEntity<?> getJwks(@PathVariable("tenant-id") TenantIdentifier tenantId) {

    JwksRequestResponse response = oidcMetaDataApi.getJwks(tenantId);
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }
}
