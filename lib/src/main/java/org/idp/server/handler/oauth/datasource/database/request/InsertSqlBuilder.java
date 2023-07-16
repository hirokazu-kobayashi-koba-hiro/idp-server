package org.idp.server.handler.oauth.datasource.database.request;

class InsertSqlBuilder {
  String sql;
  int columnSize = 25;

  InsertSqlBuilder(String identifier) {
    this.sql =
        """
            INSERT INTO public.authorization_request
            (id, token_issuer, profile, scopes, response_type, client_id, redirect_uri, state, response_mode, nonce, display, prompts, max_age, ui_locales, id_token_hint, login_hint, acr_values, claims_value, request_object, request_uri, code_challenge, code_challenge_method, authorization_details, presentation_definition, presentation_definition_uri)
            VALUES ('$1', '$2', '$3', '$4', '$5', '$6', '$7', '$8', '$9', '$10', '$11', '$12', '$13', '$14', '$15', '$16', '$17', '$18', '$19', '$20', '$21', '$22', '$23', '$24', '$25');
            """;
    this.sql = replace(1, identifier);
  }

  InsertSqlBuilder setTokenIssuer(String tokenIssuer) {
    this.sql = replace(2, tokenIssuer);
    return this;
  }

  InsertSqlBuilder setProfile(String profile) {
    this.sql = replace(3, profile);
    return this;
  }

  InsertSqlBuilder setScopes(String scopes) {
    this.sql = replace(4, scopes);
    return this;
  }

  InsertSqlBuilder setResponseType(String responseType) {
    this.sql = replace(5, responseType);
    return this;
  }

  InsertSqlBuilder setClientId(String clientId) {
    this.sql = replace(6, clientId);
    return this;
  }

  InsertSqlBuilder setRedirectUri(String redirectUri) {
    this.sql = replace(7, redirectUri);
    return this;
  }

  InsertSqlBuilder setState(String state) {
    this.sql = replace(8, state);
    return this;
  }

  InsertSqlBuilder setResponseMode(String responseMode) {
    this.sql = replace(9, responseMode);
    return this;
  }

  InsertSqlBuilder setNonce(String nonce) {
    this.sql = replace(10, nonce);
    return this;
  }

  InsertSqlBuilder setDisplay(String display) {
    this.sql = replace(11, display);
    return this;
  }

  InsertSqlBuilder setPrompts(String prompts) {
    this.sql = replace(12, prompts);
    return this;
  }

  InsertSqlBuilder setMaxAge(String maxAge) {
    this.sql = replace(13, maxAge);
    return this;
  }

  InsertSqlBuilder setUiLocales(String uiLocales) {
    this.sql = replace(14, uiLocales);
    return this;
  }

  InsertSqlBuilder setIdTokenHint(String idTokenHint) {
    this.sql = replace(15, idTokenHint);
    return this;
  }

  InsertSqlBuilder setLoginHint(String loginHint) {
    this.sql = replace(16, loginHint);
    return this;
  }

  InsertSqlBuilder setAcrValues(String acrValues) {
    this.sql = replace(17, acrValues);
    return this;
  }

  InsertSqlBuilder setClaimsValue(String claimsValue) {
    this.sql = replace(18, claimsValue);
    return this;
  }

  InsertSqlBuilder setRequestObject(String requestObject) {
    this.sql = replace(19, requestObject);
    return this;
  }

  InsertSqlBuilder setRequestUri(String requestUri) {
    this.sql = replace(20, requestUri);
    return this;
  }

  InsertSqlBuilder setCodeChallenge(String codeChallenge) {
    this.sql = replace(21, codeChallenge);
    return this;
  }

  InsertSqlBuilder setCodeChallengeMethod(String codeChallengeMethod) {
    this.sql = replace(22, codeChallengeMethod);
    return this;
  }

  InsertSqlBuilder setAuthorizationDetails(String authorizationDetails) {
    this.sql = replace(23, authorizationDetails);
    return this;
  }

  InsertSqlBuilder setPresentationDefinition(String presentationDefinition) {
    this.sql = replace(24, presentationDefinition);
    return this;
  }

  InsertSqlBuilder setPresentationDefinitionUri(String presentationDefinitionUri) {
    this.sql = replace(25, presentationDefinitionUri);
    return this;
  }

  String build() {
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
