package org.idp.sample.presentation.api.oauth;

import java.util.Map;
import org.idp.sample.application.service.OAuthFlowService;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.presentation.api.ParameterTransformable;
import org.idp.server.handler.oauth.io.OAuthLogoutResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/logout")
public class OidcLogoutV1Api implements ParameterTransformable {

  OAuthFlowService oAuthFlowService;

  public OidcLogoutV1Api(OAuthFlowService oAuthFlowService) {
    this.oAuthFlowService = oAuthFlowService;
  }

  @GetMapping
  public ResponseEntity<?> logout(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

    Map<String, String[]> params = transform(request);
    OAuthLogoutResponse response = oAuthFlowService.logout(tenantId, params);

    switch (response.status()) {
      case OK -> {
        return new ResponseEntity<>(HttpStatus.OK);
      }
      case REDIRECABLE_FOUND -> {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, response.redirectUriValue());
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
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
