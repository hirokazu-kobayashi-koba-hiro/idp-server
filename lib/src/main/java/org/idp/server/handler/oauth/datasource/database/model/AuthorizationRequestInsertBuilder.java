package org.idp.server.handler.oauth.datasource.database.model;

public class AuthorizationRequestInsertBuilder {
  String sql;
  int columnSize = 24;

  public AuthorizationRequestInsertBuilder(String identifier) {
    this.sql =
        """
            INSERT INTO public.authorization_request
            (id, token_issuer, profile, scopes, response_type, client_id, redirect_uri, state, response_mode, nonce, display, prompts, max_age, ui_locales, id_token_hint, login_hint, acr_values, claims_value, request_object, request_uri, code_challenge, code_challenge_method, authorization_details)
            VALUES ('$1', '$2', '$3', '$4', '$5', '$6', '$7', '$8', '$9', '$10', '$11', '$12', '$13', '$14', '$15', '$16', '$17', '$18', '$19', '$20', '$21', '$22', '$23');
            """;
    this.sql = replace(1, identifier);
  }

  public AuthorizationRequestInsertBuilder setTokenIssuer(String tokenIssuer) {
    this.sql = replace(2, tokenIssuer);
    return this;
  }

  public AuthorizationRequestInsertBuilder setProfile(String profile) {
    this.sql = replace(3, profile);
    return this;
  }

  public AuthorizationRequestInsertBuilder setScopes(String scopes) {
    this.sql = replace(4, scopes);
    return this;
  }

  public AuthorizationRequestInsertBuilder setResponseType(String responseType) {
    this.sql = replace(5, responseType);
    return this;
  }

  public AuthorizationRequestInsertBuilder setClientId(String clientId) {
    this.sql = replace(6, clientId);
    return this;
  }

  public AuthorizationRequestInsertBuilder setRedirectUri(String redirectUri) {
    this.sql = replace(7, redirectUri);
    return this;
  }

  public AuthorizationRequestInsertBuilder setState(String state) {
    this.sql = replace(8, state);
    return this;
  }

  public AuthorizationRequestInsertBuilder setResponseMode(String responseMode) {
    this.sql = replace(9, responseMode);
    return this;
  }

  public AuthorizationRequestInsertBuilder setNonce(String nonce) {
    this.sql = replace(10, nonce);
    return this;
  }

  public AuthorizationRequestInsertBuilder setDisplay(String display) {
    this.sql = replace(11, display);
    return this;
  }

  public AuthorizationRequestInsertBuilder setPrompts(String prompts) {
    this.sql = replace(12, prompts);
    return this;
  }

  public AuthorizationRequestInsertBuilder setMaxAge(String maxAge) {
    this.sql = replace(13, maxAge);
    return this;
  }

  public AuthorizationRequestInsertBuilder setUiLocales(String uiLocales) {
    this.sql = replace(14, uiLocales);
    return this;
  }

  public AuthorizationRequestInsertBuilder setIdTokenHint(String idTokenHint) {
    this.sql = replace(15, idTokenHint);
    return this;
  }

  public AuthorizationRequestInsertBuilder setLoginHint(String loginHint) {
    this.sql = replace(16, loginHint);
    return this;
  }

  public AuthorizationRequestInsertBuilder setAcrValues(String acrValues) {
    this.sql = replace(17, acrValues);
    return this;
  }

  public AuthorizationRequestInsertBuilder setClaimsValue(String claimsValue) {
    this.sql = replace(18, claimsValue);
    return this;
  }

  public AuthorizationRequestInsertBuilder setRequestObject(String requestObject) {
    this.sql = replace(19, requestObject);
    return this;
  }

  public AuthorizationRequestInsertBuilder setRequestUri(String requestUri) {
    this.sql = replace(20, requestUri);
    return this;
  }

  public AuthorizationRequestInsertBuilder setCodeChallenge(String codeChallenge) {
    this.sql = replace(21, codeChallenge);
    return this;
  }

  public AuthorizationRequestInsertBuilder setCodeChallengeMethod(String codeChallengeMethod) {
    this.sql = replace(22, codeChallengeMethod);
    return this;
  }

  public AuthorizationRequestInsertBuilder setAuthorizationDetails(String authorizationDetails) {
    this.sql = replace(23, authorizationDetails);
    return this;
  }

  public String build() {
    for (int i = 1; i <= columnSize; i++) {
      this.sql = sql.replace("'$" + i + "'", "''");
    }
    System.out.println(sql);
    return sql;
  }

  private String replace(int index, String value) {
    return sql.replace(String.format("'$%d'", index), String.format("'%s'", value));
  }
}
