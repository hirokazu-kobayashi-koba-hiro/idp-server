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

package org.idp.server.adapters.springboot.application.restapi.authentication.device;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.core.openid.authentication.AuthenticationTransactionApi;
import org.idp.server.core.openid.authentication.AuthenticationTransactionQueries;
import org.idp.server.core.openid.authentication.io.AuthenticationTransactionFindingResponse;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/v1/authentication-devices")
public class AuthenticationDeviceV1Api implements ParameterTransformable {

  AuthenticationTransactionApi authenticationTransactionApi;
  LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationDeviceV1Api.class);

  public AuthenticationDeviceV1Api(IdpServerApplication idpServerApplication) {
    this.authenticationTransactionApi = idpServerApplication.authenticationApi();
  }

  @GetMapping("/{device-id}/authentications")
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("device-id") AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationTransactionFindingResponse response =
        authenticationTransactionApi.findList(
            tenantIdentifier,
            authenticationDeviceIdentifier,
            new AuthenticationTransactionQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/logs")
  public ResponseEntity<?> post(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody Map<String, Object> requestBody) {

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(requestBody);
    log.info(jsonNodeWrapper.toJson());

    return new ResponseEntity<>(HttpStatus.OK);
  }
}
