package org.idp.server.adapters.springboot.restapi;

import java.util.Map;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.function.TokenFunction;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens/introspection")
public class TokenIntrospectionV1Api implements ParameterTransformable {

  TokenFunction tokenFunction;

  public TokenIntrospectionV1Api(IdpServerApplication idpServerApplication) {
    this.tokenFunction = idpServerApplication.tokenFunction();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Map<String, String[]> request = transform(body);

    TokenIntrospectionResponse response = tokenFunction.inspect(tenantId, request);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
