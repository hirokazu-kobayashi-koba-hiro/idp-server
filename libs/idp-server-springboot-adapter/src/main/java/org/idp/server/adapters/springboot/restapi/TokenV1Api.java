package org.idp.server.adapters.springboot.restapi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.usecases.IdpServerApplication;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.token.TokenApi;
import org.idp.server.core.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.basic.type.security.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens")
public class TokenV1Api implements ParameterTransformable {

  TokenApi tokenApi;

  public TokenV1Api(IdpServerApplication idpServerApplication) {
    this.tokenApi = idpServerApplication.tokenAPi();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> request = transform(body);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenRequestResponse response =
        tokenApi.request(
            tenantIdentifier, request, authorizationHeader, clientCert, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setAll(response.responseHeaders());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/introspection")
  public ResponseEntity<?> inspect(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> request = transform(body);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenIntrospectionResponse response =
        tokenApi.inspect(tenantIdentifier, request, requestAttributes);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/revocation")
  public ResponseEntity<?> revoke(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> request = transform(body);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenRevocationResponse response =
        tokenApi.revoke(
            tenantIdentifier, request, authorizationHeader, clientCert, requestAttributes);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
