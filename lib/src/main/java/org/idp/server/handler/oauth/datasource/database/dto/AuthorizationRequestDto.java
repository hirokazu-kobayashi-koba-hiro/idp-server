package org.idp.server.handler.oauth.datasource.database.dto;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.*;

public class AuthorizationRequestDto implements JsonReadable {

  String identifier;
  String tokenIssuer;
  String profile;
  String scopes;
  String responseType;
  String clientId;
  String redirectUri;
  String state;
  String responseMode;
  String nonce;
  String display;
  String prompts;
  String maxAge;
  String uiLocales;
  String idTokenHint;
  String loginHint;
  List<String> acrValues;
  String claimsValue;
  String requestObject;
  String requestUri;
  String claimsPayload;
  String codeChallenge;
  String codeChallengeMethod;
  List<Map<String, Object>> authorizationDetails;

  public AuthorizationRequestDto() {}

  public String identifier() {
    return identifier;
  }

  public AuthorizationRequestDto setIdentifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  public String tokenIssuer() {
    return tokenIssuer;
  }

  public AuthorizationRequestDto setTokenIssuer(String tokenIssuer) {
    this.tokenIssuer = tokenIssuer;
    return this;
  }

  public String profile() {
    return profile;
  }

  public AuthorizationRequestDto setProfile(String profile) {
    this.profile = profile;
    return this;
  }

  public String scopes() {
    return scopes;
  }

  public AuthorizationRequestDto setScopes(String scopes) {
    this.scopes = scopes;
    return this;
  }

  public String responseType() {
    return responseType;
  }

  public AuthorizationRequestDto setResponseType(String responseType) {
    this.responseType = responseType;
    return this;
  }

  public String clientId() {
    return clientId;
  }

  public AuthorizationRequestDto setClientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  public String redirectUri() {
    return redirectUri;
  }

  public AuthorizationRequestDto setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
    return this;
  }

  public String state() {
    return state;
  }

  public AuthorizationRequestDto setState(String state) {
    this.state = state;
    return this;
  }

  public String responseMode() {
    return responseMode;
  }

  public AuthorizationRequestDto setResponseMode(String responseMode) {
    this.responseMode = responseMode;
    return this;
  }

  public String nonce() {
    return nonce;
  }

  public AuthorizationRequestDto setNonce(String nonce) {
    this.nonce = nonce;
    return this;
  }

  public String display() {
    return display;
  }

  public AuthorizationRequestDto setDisplay(String display) {
    this.display = display;
    return this;
  }

  public String prompts() {
    return prompts;
  }

  public AuthorizationRequestDto setPrompts(String prompts) {
    this.prompts = prompts;
    return this;
  }

  public String maxAge() {
    return maxAge;
  }

  public AuthorizationRequestDto setMaxAge(String maxAge) {
    this.maxAge = maxAge;
    return this;
  }

  public String uiLocales() {
    return uiLocales;
  }

  public AuthorizationRequestDto setUiLocales(String uiLocales) {
    this.uiLocales = uiLocales;
    return this;
  }

  public String idTokenHint() {
    return idTokenHint;
  }

  public AuthorizationRequestDto setIdTokenHint(String idTokenHint) {
    this.idTokenHint = idTokenHint;
    return this;
  }

  public String loginHint() {
    return loginHint;
  }

  public AuthorizationRequestDto setLoginHint(String loginHint) {
    this.loginHint = loginHint;
    return this;
  }

  public List<String> acrValues() {
    return acrValues;
  }

  public AuthorizationRequestDto setAcrValues(List<String> acrValues) {
    this.acrValues = acrValues;
    return this;
  }

  public String claimsValue() {
    return claimsValue;
  }

  public AuthorizationRequestDto setClaimsValue(String claimsValue) {
    this.claimsValue = claimsValue;
    return this;
  }

  public String requestObject() {
    return requestObject;
  }

  public AuthorizationRequestDto setRequestObject(String requestObject) {
    this.requestObject = requestObject;
    return this;
  }

  public String requestUri() {
    return requestUri;
  }

  public AuthorizationRequestDto setRequestUri(String requestUri) {
    this.requestUri = requestUri;
    return this;
  }

  public String claimsPayload() {
    return claimsPayload;
  }

  public AuthorizationRequestDto setClaimsPayload(String claimsPayload) {
    this.claimsPayload = claimsPayload;
    return this;
  }

  public String codeChallenge() {
    return codeChallenge;
  }

  public AuthorizationRequestDto setCodeChallenge(String codeChallenge) {
    this.codeChallenge = codeChallenge;
    return this;
  }

  public String codeChallengeMethod() {
    return codeChallengeMethod;
  }

  public AuthorizationRequestDto setCodeChallengeMethod(String codeChallengeMethod) {
    this.codeChallengeMethod = codeChallengeMethod;
    return this;
  }

  public List<Map<String, Object>> authorizationDetails() {
    return authorizationDetails;
  }

  public AuthorizationRequestDto setAuthorizationDetails(
      List<Map<String, Object>> authorizationDetails) {
    this.authorizationDetails = authorizationDetails;
    return this;
  }
}
