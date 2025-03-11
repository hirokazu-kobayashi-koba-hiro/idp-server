package org.idp.server.adapters.springboot.presentation.api.oauth;

import java.util.Map;
import org.idp.server.adapters.springboot.application.service.OAuthFlowService;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.type.extension.Pairs;
import org.idp.server.adapters.springboot.domain.model.tenant.Tenant;
import org.idp.server.adapters.springboot.presentation.api.ParameterTransformable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
public class OAuthFederationCallbackV1Api implements ParameterTransformable {

  OAuthFlowService oAuthFlowService;

  public OAuthFederationCallbackV1Api(OAuthFlowService oAuthFlowService) {
    this.oAuthFlowService = oAuthFlowService;
  }

  @PostMapping("/api/v1/authorizations/federations/callback")
  public ResponseEntity<?> callbackFederation(
      @RequestBody(required = false) MultiValueMap<String, String> body) {

    Map<String, String[]> params = transform(body);
    Pairs<Tenant, FederationCallbackResponse> result = oAuthFlowService.callbackFederation(params);
    Tenant tenant = result.getLeft();
    FederationCallbackResponse callbackResponse = result.getRight();

    switch (callbackResponse.status()) {
      case OK -> {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");

        Map<String, String> contents =
            Map.of(
                "id",
                callbackResponse.authorizationRequestId(),
                "tenant_id",
                tenant.identifierValue());

        return new ResponseEntity<>(contents, headers, HttpStatus.OK);
      }
      case BAD_REQUEST -> {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }
}
