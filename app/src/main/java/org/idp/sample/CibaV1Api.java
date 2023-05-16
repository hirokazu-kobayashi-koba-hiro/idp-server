package org.idp.sample;

import java.util.Map;
import org.idp.server.CibaApi;
import org.idp.server.IdpServerApplication;
import org.idp.server.handler.ciba.io.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/backchannel/authentications")
public class CibaV1Api implements ParameterTransformable {

  CibaApi cibaApi;
  UserMockService userMockService;

  public CibaV1Api(IdpServerApplication idpServerApplication, UserMockService userMockService) {
    this.cibaApi = idpServerApplication.cibaApi();
    this.userMockService = userMockService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @PathVariable("tenant-id") String tenantId) {
    Map<String, String[]> params = transform(body);
    Tenant tenant = Tenant.of(tenantId);
    CibaRequest cibaRequest = new CibaRequest(authorizationHeader, params, tenant.issuer());
    CibaRequestResponse response = cibaApi.request(cibaRequest, userMockService);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", response.contentTypeValue());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/automated-complete")
  public ResponseEntity<?> complete(
      @RequestParam("auth_req_id") String authReqId,
      @RequestParam("action") String action,
      @PathVariable("tenant-id") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    if (action.equals("allow")) {
      CibaAuthorizeRequest cibaAuthorizeRequest =
          new CibaAuthorizeRequest(authReqId, tenant.issuer());
      CibaAuthorizeResponse authorizeResponse = cibaApi.authorize(cibaAuthorizeRequest);
      return new ResponseEntity<>(HttpStatus.valueOf(authorizeResponse.statusCode()));
    }
    CibaDenyRequest cibaDenyRequest = new CibaDenyRequest(authReqId, tenant.issuer());
    CibaDenyResponse cibaDenyResponse = cibaApi.deny(cibaDenyRequest);
    return new ResponseEntity<>(HttpStatus.valueOf(cibaDenyResponse.statusCode()));
  }
}
