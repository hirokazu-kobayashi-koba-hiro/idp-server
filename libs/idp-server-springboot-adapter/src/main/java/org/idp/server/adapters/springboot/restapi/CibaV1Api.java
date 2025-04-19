package org.idp.server.adapters.springboot.restapi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.ciba.CibaFlowApi;
import org.idp.server.core.ciba.handler.io.CibaAuthorizeResponse;
import org.idp.server.core.ciba.handler.io.CibaDenyResponse;
import org.idp.server.core.ciba.handler.io.CibaRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.security.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/backchannel/authentications")
public class CibaV1Api implements ParameterTransformable {

  CibaFlowApi cibaFlowApi;

  public CibaV1Api(IdpServerApplication idpServerApplication) {
    this.cibaFlowApi = idpServerApplication.cibaFlowApi();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> params = transform(body);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    CibaRequestResponse response =
        cibaFlowApi.request(tenantId, params, authorizationHeader, clientCert, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", response.contentTypeValue());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/automated-complete")
  public ResponseEntity<?> complete(
      @RequestParam("auth_req_id") String authReqId,
      @RequestParam("action") String action,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    if (action.equals("allow")) {

      CibaAuthorizeResponse authorizeResponse =
          cibaFlowApi.authorize(tenantIdentifier, authReqId, requestAttributes);
      return new ResponseEntity<>(HttpStatus.valueOf(authorizeResponse.statusCode()));
    }

    CibaDenyResponse cibaDenyResponse =
        cibaFlowApi.deny(tenantIdentifier, authReqId, requestAttributes);
    return new ResponseEntity<>(HttpStatus.valueOf(cibaDenyResponse.statusCode()));
  }
}
