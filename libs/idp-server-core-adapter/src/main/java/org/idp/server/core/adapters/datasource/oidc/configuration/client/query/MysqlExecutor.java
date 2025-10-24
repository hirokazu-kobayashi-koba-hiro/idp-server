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

package org.idp.server.core.adapters.datasource.oidc.configuration.client.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientQueries;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements ClientConfigSqlExecutor {

  JsonConverter jsonConverter;

  public MysqlExecutor() {
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void insert(Tenant tenant, ClientConfiguration clientConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO client_configuration (id, id_alias, tenant_id, payload)
            VALUES (?, ?, ?, ?)
            """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientIdentifier().value());
    params.add(clientConfiguration.clientIdAlias());
    params.add(tenant.identifierValue());
    params.add(payload);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByAlias(Tenant tenant, RequestedClientId requestedClientId) {
    return selectByAlias(tenant, requestedClientId, false);
  }

  @Override
  public Map<String, String> selectByAlias(
      Tenant tenant, RequestedClientId requestedClientId, boolean includeDisabled) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplateClientIdAlias =
        """
                        SELECT id, id_alias, tenant_id, payload, created_at, updated_at
                        FROM client_configuration
                        WHERE tenant_id = ? AND id_alias = ?"""
            + (includeDisabled ? "" : " AND enabled = true")
            + ";";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(requestedClientId.value());

    return sqlExecutor.selectOne(sqlTemplateClientIdAlias, params);
  }

  @Override
  public Map<String, String> selectById(Tenant tenant, ClientIdentifier clientIdentifier) {
    return selectById(tenant, clientIdentifier, false);
  }

  @Override
  public Map<String, String> selectById(
      Tenant tenant, ClientIdentifier clientIdentifier, boolean includeDisabled) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                        SELECT id, id_alias, tenant_id, payload, created_at, updated_at
                        FROM client_configuration
                        WHERE tenant_id = ? AND id = ?"""
            + (includeDisabled ? "" : " AND enabled = true")
            + ";";
    List<Object> params = List.of(tenant.identifierValue(), clientIdentifier.value());
    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    return selectList(tenant, limit, offset, true);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, int limit, int offset, boolean includeDisabled) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                        SELECT id, id_alias, tenant_id, payload, created_at, updated_at
                        FROM client_configuration
                        WHERE tenant_id = ?"""
            + (includeDisabled ? "" : " AND enabled = true")
            + " limit ? offset ?;";
    List<Object> params = List.of(tenant.identifierValue(), limit, offset);
    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, ClientQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder where = new StringBuilder("WHERE tenant_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    if (queries.hasFrom()) {
      where.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      where.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasClientId()) {
      where.append(" AND id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasClientIdAlias()) {
      where.append(" AND id_alias = ?");
      params.add(queries.clientIdAlias());
    }

    if (queries.hasClientName()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.client_name')) LIKE ?");
      params.add("%" + queries.clientName() + "%");
    }

    if (queries.hasClientUri()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.client_uri')) LIKE ?");
      params.add("%" + queries.clientUri() + "%");
    }

    if (queries.hasApplicationType()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.application_type')) = ?");
      params.add(queries.applicationType());
    }

    if (queries.hasGrantTypes()) {
      where.append(" AND JSON_CONTAINS(JSON_EXTRACT(payload, '$.grant_types'), ?)");
      params.add("\"" + queries.grantTypes() + "\"");
    }

    if (queries.hasResponseTypes()) {
      where.append(" AND JSON_CONTAINS(JSON_EXTRACT(payload, '$.response_types'), ?)");
      params.add("\"" + queries.responseTypes() + "\"");
    }

    if (queries.hasTokenEndpointAuthMethod()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.token_endpoint_auth_method')) = ?");
      params.add(queries.tokenEndpointAuthMethod());
    }

    if (queries.hasRedirectUris()) {
      where.append(" AND JSON_CONTAINS(JSON_EXTRACT(payload, '$.redirect_uris'), ?)");
      params.add("\"" + queries.redirectUris() + "\"");
    }

    if (queries.hasScope()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.scope')) LIKE ?");
      params.add("%" + queries.scope() + "%");
    }

    if (queries.hasEnabled()) {
      where.append(" AND enabled = ?");
      params.add(queries.enabled());
    }

    if (queries.hasBackchannelTokenDeliveryMode()) {
      where.append(
          " AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.backchannel_token_delivery_mode')) = ?");
      params.add(queries.backchannelTokenDeliveryMode());
    }

    if (queries.hasBackchannelUserCodeParameter()) {
      where.append(
          " AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.backchannel_user_code_parameter')) = ?");
      params.add(queries.backchannelUserCodeParameter());
    }

    if (queries.hasAuthorizationDetailsTypes()) {
      where.append(" AND JSON_CONTAINS(JSON_EXTRACT(payload, '$.authorization_details_types'), ?)");
      params.add("\"" + queries.authorizationDetailsTypes() + "\"");
    }

    if (queries.hasTlsClientAuthSubjectDn()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.tls_client_auth_subject_dn')) = ?");
      params.add(queries.tlsClientAuthSubjectDn());
    }

    if (queries.hasTlsClientCertificateBoundAccessTokens()) {
      where.append(
          " AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.tls_client_certificate_bound_access_tokens')) = ?");
      params.add(queries.tlsClientCertificateBoundAccessTokens());
    }

    if (queries.hasSoftwareId()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.software_id')) = ?");
      params.add(queries.softwareId());
    }

    if (queries.hasSoftwareVersion()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.software_version')) = ?");
      params.add(queries.softwareVersion());
    }

    String sqlTemplate =
        """
        SELECT id, id_alias, tenant_id, payload, created_at, updated_at
        FROM client_configuration
        """
            + where
            + """
         LIMIT ?
         OFFSET ?
        """;

    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectTotalCount(Tenant tenant, ClientQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder where = new StringBuilder("WHERE tenant_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    if (queries.hasFrom()) {
      where.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      where.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasClientId()) {
      where.append(" AND id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasClientIdAlias()) {
      where.append(" AND id_alias = ?");
      params.add(queries.clientIdAlias());
    }

    if (queries.hasClientName()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.client_name')) LIKE ?");
      params.add("%" + queries.clientName() + "%");
    }

    if (queries.hasClientUri()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.client_uri')) LIKE ?");
      params.add("%" + queries.clientUri() + "%");
    }

    if (queries.hasApplicationType()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.application_type')) = ?");
      params.add(queries.applicationType());
    }

    if (queries.hasGrantTypes()) {
      where.append(" AND JSON_CONTAINS(JSON_EXTRACT(payload, '$.grant_types'), ?)");
      params.add("\"" + queries.grantTypes() + "\"");
    }

    if (queries.hasResponseTypes()) {
      where.append(" AND JSON_CONTAINS(JSON_EXTRACT(payload, '$.response_types'), ?)");
      params.add("\"" + queries.responseTypes() + "\"");
    }

    if (queries.hasTokenEndpointAuthMethod()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.token_endpoint_auth_method')) = ?");
      params.add(queries.tokenEndpointAuthMethod());
    }

    if (queries.hasRedirectUris()) {
      where.append(" AND JSON_CONTAINS(JSON_EXTRACT(payload, '$.redirect_uris'), ?)");
      params.add("\"" + queries.redirectUris() + "\"");
    }

    if (queries.hasScope()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.scope')) LIKE ?");
      params.add("%" + queries.scope() + "%");
    }

    if (queries.hasEnabled()) {
      where.append(" AND enabled = ?");
      params.add(queries.enabled());
    }

    if (queries.hasBackchannelTokenDeliveryMode()) {
      where.append(
          " AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.backchannel_token_delivery_mode')) = ?");
      params.add(queries.backchannelTokenDeliveryMode());
    }

    if (queries.hasBackchannelUserCodeParameter()) {
      where.append(
          " AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.backchannel_user_code_parameter')) = ?");
      params.add(queries.backchannelUserCodeParameter());
    }

    if (queries.hasAuthorizationDetailsTypes()) {
      where.append(" AND JSON_CONTAINS(JSON_EXTRACT(payload, '$.authorization_details_types'), ?)");
      params.add("\"" + queries.authorizationDetailsTypes() + "\"");
    }

    if (queries.hasTlsClientAuthSubjectDn()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.tls_client_auth_subject_dn')) = ?");
      params.add(queries.tlsClientAuthSubjectDn());
    }

    if (queries.hasTlsClientCertificateBoundAccessTokens()) {
      where.append(
          " AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.tls_client_certificate_bound_access_tokens')) = ?");
      params.add(queries.tlsClientCertificateBoundAccessTokens());
    }

    if (queries.hasSoftwareId()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.software_id')) = ?");
      params.add(queries.softwareId());
    }

    if (queries.hasSoftwareVersion()) {
      where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.software_version')) = ?");
      params.add(queries.softwareVersion());
    }

    String sqlTemplate =
        """
        SELECT COUNT(*) as count
        FROM client_configuration
        """ + where;

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, ClientConfiguration clientConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE client_configuration
                SET id_alias = ?,
                payload = ?
                WHERE tenant_id = ?
                AND id = ?
                """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientIdAlias());
    params.add(payload);
    params.add(tenant.identifierValue());
    params.add(clientConfiguration.clientIdentifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, RequestedClientId requestedClientId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                DELETE FROM client_configuration
                WHERE tenant_id = ?
                AND id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(requestedClientId.value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
