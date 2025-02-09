package org.idp.sample.presentation.api;

import java.util.Map;
import org.idp.sample.application.service.TenantService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.TokenIntrospectionApi;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens/introspection")
public class TokenIntrospectionV1Api implements ParameterTransformable {

  TokenIntrospectionApi tokenIntrospectionApi;
  TenantService tenantService;

  public TokenIntrospectionV1Api(
      IdpServerApplication idpServerApplication, TenantService tenantService) {
    this.tokenIntrospectionApi = idpServerApplication.tokenIntrospectionApi();
    this.tenantService = tenantService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Map<String, String[]> request = transform(body);
    Tenant tenant = tenantService.get(tenantId);
    TokenIntrospectionRequest tokenIntrospectionRequest =
        new TokenIntrospectionRequest(request, tenant.issuer());
    TokenIntrospectionResponse response = tokenIntrospectionApi.inspect(tokenIntrospectionRequest);
    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
