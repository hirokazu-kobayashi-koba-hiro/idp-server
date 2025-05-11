package org.idp.server.adapters.springboot.application.restapi.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.federation.*;
import org.idp.server.core.federation.io.FederationCallbackRequest;
import org.idp.server.core.oidc.OAuthFlowApi;
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

  @PostMapping("/v1/authorizations/federations/{federation-type}/{sso-provider-name}/callback")
  public ResponseEntity<?> callbackFederation(
      @PathVariable("federation-type") FederationType federationType,
      @PathVariable("sso-provider-name") SsoProvider ssoProvider,
      @RequestBody(required = false) MultiValueMap<String, String> body,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    Map<String, String[]> params = transform(body);
    FederationCallbackRequest federationCallbackRequest = new FederationCallbackRequest(params);

    FederationInteractionResult result =
        oAuthFlowApi.callbackFederation(
            federationCallbackRequest.tenantIdentifier(),
            federationType,
            ssoProvider,
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
