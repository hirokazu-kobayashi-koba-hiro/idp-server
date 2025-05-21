package org.idp.server.adapters.springboot.application.restapi.ciba;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.extension.ciba.CibaFlowApi;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/v1/ciba/{id}/{interaction-type}")
public class CibaInteractionV1Api implements ParameterTransformable {

  CibaFlowApi cibaFlowApi;

  public CibaInteractionV1Api(IdpServerApplication idpServerApplication) {
    this.cibaFlowApi = idpServerApplication.cibaFlowApi();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id")
          BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier,
      @PathVariable("interaction-type") AuthenticationInteractionType type,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    AuthenticationInteractionRequest request = new AuthenticationInteractionRequest(requestBody);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationInteractionRequestResult result =
        cibaFlowApi.interact(
            tenantIdentifier,
            backchannelAuthenticationRequestIdentifier,
            type,
            request,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        result.response(), httpHeaders, HttpStatus.valueOf(result.statusCode()));
  }
}
