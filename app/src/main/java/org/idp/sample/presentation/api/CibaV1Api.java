package org.idp.sample.presentation.api;

import java.util.Map;
import org.idp.sample.application.service.CibaFlowService;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.handler.ciba.io.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/backchannel/authentications")
public class CibaV1Api implements ParameterTransformable {

  CibaFlowService cibaFlowService;

  public CibaV1Api(CibaFlowService cibaFlowService) {
    this.cibaFlowService = cibaFlowService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

    Map<String, String[]> params = transform(body);

    CibaRequestResponse response =
        cibaFlowService.request(tenantId, params, authorizationHeader, clientCert);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", response.contentTypeValue());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/automated-complete")
  public ResponseEntity<?> complete(
      @RequestParam("auth_req_id") String authReqId,
      @RequestParam("action") String action,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

    if (action.equals("allow")) {

      CibaAuthorizeResponse authorizeResponse = cibaFlowService.authorize(tenantId, authReqId);
      return new ResponseEntity<>(HttpStatus.valueOf(authorizeResponse.statusCode()));
    }

    CibaDenyResponse cibaDenyResponse = cibaFlowService.deny(tenantId, authReqId);
    return new ResponseEntity<>(HttpStatus.valueOf(cibaDenyResponse.statusCode()));
  }
}
