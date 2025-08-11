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

package org.idp.server.adapters.springboot.application.restapi.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.core.openid.federation.*;
import org.idp.server.core.openid.federation.io.FederationCallbackRequest;
import org.idp.server.core.openid.oauth.OAuthFlowApi;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
public class OAuthFederationCallbackV1Api implements ParameterTransformable {

  OAuthFlowApi oAuthFlowApi;

  public OAuthFederationCallbackV1Api(IdpServerApplication idpServerApplication) {
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
  }

  @PostMapping("/v1/authorizations/federations/{federation-type}/callback")
  public ResponseEntity<?> callbackFederation(
      @PathVariable("federation-type") FederationType federationType,
      @RequestBody(required = false) MultiValueMap<String, String> body,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    Map<String, String[]> params = transform(body);
    FederationCallbackRequest federationCallbackRequest = new FederationCallbackRequest(params);

    FederationInteractionResult result =
        oAuthFlowApi.callbackFederation(
            federationCallbackRequest.tenantIdentifier(),
            federationType,
            federationCallbackRequest.ssoProvider(),
            federationCallbackRequest,
            requestAttributes);

    switch (result.status()) {
      case SUCCESS -> {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");

        return new ResponseEntity<>(result.response(), headers, HttpStatus.OK);
      }
      case CLIENT_ERROR -> {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");

        return new ResponseEntity<>(result.response(), headers, HttpStatus.BAD_REQUEST);
      }
      default -> {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");

        return new ResponseEntity<>(result.response(), headers, HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }
}
