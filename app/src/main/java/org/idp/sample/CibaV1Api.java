package org.idp.sample;

import java.util.Map;
import org.idp.sample.user.UserService;
import org.idp.server.CibaApi;
import org.idp.server.IdpServerApplication;
import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.ciba.UserCriteria;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.handler.ciba.io.*;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.UserCode;
import org.idp.server.type.oauth.TokenIssuer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/backchannel/authentications")
public class CibaV1Api implements CibaRequestDelegate, ParameterTransformable {

  CibaApi cibaApi;
  UserService userService;

  public CibaV1Api(IdpServerApplication idpServerApplication, UserService userService) {
    this.cibaApi = idpServerApplication.cibaApi();
    this.userService = userService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") String tenantId) {
    Map<String, String[]> params = transform(body);
    Tenant tenant = Tenant.of(tenantId);
    CibaRequest cibaRequest = new CibaRequest(authorizationHeader, params, tenant.issuer());
    cibaRequest.setClientCert(clientCert);
    CibaRequestResponse response = cibaApi.request(cibaRequest, this);
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

  @Override
  public User find(TokenIssuer tokenIssuer, UserCriteria criteria) {
    if (criteria.hasLoginHint()) {
      return userService.find(criteria.loginHint().value());
    }
    return User.notFound();
  }

  @Override
  public boolean authenticate(TokenIssuer tokenIssuer, User user, UserCode userCode) {
    return userService.authenticate(user, userCode.value());
  }

  @Override
  public void notify(
      TokenIssuer tokenIssuer, User user, BackchannelAuthenticationRequest request) {}
}
