package org.idp.server.core.ciba.handler.io;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.ContentType;
import org.idp.server.basic.type.oauth.ExpiresIn;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.AcrValues;
import org.idp.server.core.ciba.CibaRequestContext;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;

// TODO to be more readable name
public class CibaIssueResponse {
  CibaRequestStatus status;
  CibaRequestContext cibaRequestContext;
  BackchannelAuthenticationRequest request;
  BackchannelAuthenticationResponse response;
  User user;
  BackchannelAuthenticationErrorResponse errorResponse;
  ContentType contentType;

  public CibaIssueResponse(
      CibaRequestStatus status,
      CibaRequestContext cibaRequestContext,
      BackchannelAuthenticationResponse response,
      User user) {
    this.status = status;
    this.cibaRequestContext = cibaRequestContext;
    this.request = cibaRequestContext.backchannelAuthenticationRequest();
    this.response = response;
    this.user = user;
    this.errorResponse = new BackchannelAuthenticationErrorResponse();
    this.contentType = ContentType.application_json;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public BackchannelAuthenticationRequest request() {
    return request;
  }

  public BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier() {
    return request.identifier();
  }

  public BackchannelAuthenticationResponse response() {
    return response;
  }

  public User user() {
    return user;
  }

  public List<String> requiredAnyOfAuthenticationTypes() {
    List<String> methods = new ArrayList<>();
    if (cibaRequestContext.isFapiProfile()) {
      methods.add("webauthn");
      methods.add("fido-uaf");
      methods.add("fido2");
    }
    return methods;
  }

  public ExpiresIn expiresIn() {
    return cibaRequestContext.expiresIn();
  }

  public BackchannelAuthenticationErrorResponse errorResponse() {
    return errorResponse;
  }

  public ContentType contentType() {
    return contentType;
  }

  public String contentTypeValue() {
    return contentType.value();
  }

  public String contents() {
    if (status.isOK()) {
      return response.contents();
    }
    return errorResponse.contents();
  }

  public boolean isOK() {
    return status.isOK();
  }

  public CibaRequestResponse toResponse() {
    return new CibaRequestResponse(status, response);
  }

  public AcrValues acrValues() {
    return cibaRequestContext.acrValues();
  }

  public Scopes scopes() {
    return cibaRequestContext.scopes();
  }

  public AuthenticationPolicy findSatisfiedAuthenticationPolicy() {
    List<AuthenticationPolicy> authenticationPolicies = cibaRequestContext.authenticationPolicies();
    return authenticationPolicies.stream()
        .filter(
            policy ->
                policy.anyMatch(AuthorizationFlow.CIBA, request.acrValues(), request.scopes()))
        .max(Comparator.comparingInt(AuthenticationPolicy::priority))
        .orElse(new AuthenticationPolicy());
  }
}
