/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.oauth.configuration.client;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.uuid.UuidConvertable;

/**
 * Query parameters for client configuration search operations. Follows the same pattern as
 * UserQueries for consistent API design.
 */
public class ClientQueries implements UuidConvertable {
  Map<String, String> values;

  public ClientQueries() {}

  public ClientQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  // Date range queries
  public boolean hasFrom() {
    return values.containsKey("from");
  }

  public LocalDateTime from() {
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public boolean hasTo() {
    return values.containsKey("to");
  }

  public LocalDateTime to() {
    return LocalDateTimeParser.parse(values.get("to"));
  }

  // Client identification
  public boolean hasClientId() {
    return values.containsKey("client_id");
  }

  public String clientId() {
    return values.get("client_id");
  }

  public boolean hasClientIdAlias() {
    return values.containsKey("client_id_alias");
  }

  public String clientIdAlias() {
    return values.get("client_id_alias");
  }

  // Client metadata
  public boolean hasClientName() {
    return values.containsKey("client_name");
  }

  public String clientName() {
    return values.get("client_name");
  }

  public boolean hasClientUri() {
    return values.containsKey("client_uri");
  }

  public String clientUri() {
    return values.get("client_uri");
  }

  // OAuth configuration
  public boolean hasApplicationType() {
    return values.containsKey("application_type");
  }

  public String applicationType() {
    return values.get("application_type");
  }

  public boolean hasGrantTypes() {
    return values.containsKey("grant_types");
  }

  public String grantTypes() {
    return values.get("grant_types");
  }

  public boolean hasResponseTypes() {
    return values.containsKey("response_types");
  }

  public String responseTypes() {
    return values.get("response_types");
  }

  public boolean hasTokenEndpointAuthMethod() {
    return values.containsKey("token_endpoint_auth_method");
  }

  public String tokenEndpointAuthMethod() {
    return values.get("token_endpoint_auth_method");
  }

  // Redirect URIs and scope
  public boolean hasRedirectUris() {
    return values.containsKey("redirect_uris");
  }

  public String redirectUris() {
    return values.get("redirect_uris");
  }

  public boolean hasScope() {
    return values.containsKey("scope");
  }

  public String scope() {
    return values.get("scope");
  }

  // Client status
  public boolean hasEnabled() {
    return values.containsKey("enabled");
  }

  public boolean enabled() {
    return Boolean.parseBoolean(values.get("enabled"));
  }

  // CIBA specific queries
  public boolean hasBackchannelTokenDeliveryMode() {
    return values.containsKey("backchannel_token_delivery_mode");
  }

  public String backchannelTokenDeliveryMode() {
    return values.get("backchannel_token_delivery_mode");
  }

  public boolean hasBackchannelUserCodeParameter() {
    return values.containsKey("backchannel_user_code_parameter");
  }

  public boolean backchannelUserCodeParameter() {
    return Boolean.parseBoolean(values.get("backchannel_user_code_parameter"));
  }

  // Rich Authorization Requests
  public boolean hasAuthorizationDetailsTypes() {
    return values.containsKey("authorization_details_types");
  }

  public String authorizationDetailsTypes() {
    return values.get("authorization_details_types");
  }

  // mTLS related
  public boolean hasTlsClientAuthSubjectDn() {
    return values.containsKey("tls_client_auth_subject_dn");
  }

  public String tlsClientAuthSubjectDn() {
    return values.get("tls_client_auth_subject_dn");
  }

  public boolean hasTlsClientCertificateBoundAccessTokens() {
    return values.containsKey("tls_client_certificate_bound_access_tokens");
  }

  public boolean tlsClientCertificateBoundAccessTokens() {
    return Boolean.parseBoolean(values.get("tls_client_certificate_bound_access_tokens"));
  }

  // Software information
  public boolean hasSoftwareId() {
    return values.containsKey("software_id");
  }

  public String softwareId() {
    return values.get("software_id");
  }

  public boolean hasSoftwareVersion() {
    return values.containsKey("software_version");
  }

  public String softwareVersion() {
    return values.get("software_version");
  }

  // Extension details (following UserQueries pattern)
  public boolean hasDetails() {
    return !details().isEmpty();
  }

  public Map<String, String> details() {
    Map<String, String> details = new HashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("details.")) {
        String value = entry.getValue();
        details.put(key.replace("details.", ""), value);
      }
    }
    return details;
  }

  // Pagination with defaults (following UserQueries pattern)
  public int limit() {
    if (!values.containsKey("limit")) {
      return 20;
    }
    return Integer.parseInt(values.get("limit"));
  }

  public int offset() {
    if (!values.containsKey("offset")) {
      return 0;
    }
    return Integer.parseInt(values.get("offset"));
  }
}
