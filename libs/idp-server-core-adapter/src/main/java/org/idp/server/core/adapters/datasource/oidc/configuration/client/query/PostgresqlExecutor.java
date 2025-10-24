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

public class PostgresqlExecutor implements ClientConfigSqlExecutor {

  JsonConverter jsonConverter;

  public PostgresqlExecutor() {
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void insert(Tenant tenant, ClientConfiguration clientConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO client_configuration (id, id_alias, tenant_id, payload)
            VALUES (
            ?::uuid,
            ?,
            ?::uuid,
            ?::jsonb
            )
            """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientIdentifier().valueAsUuid());
    params.add(clientConfiguration.clientIdAlias());
    params.add(tenant.identifierUUID());
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
                        WHERE tenant_id = ?::uuid
                        AND id_alias = ?"""
            + (includeDisabled ? "" : "\n                        AND enabled = true")
            + ";";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
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
                        WHERE tenant_id = ?::uuid
                        AND id = ?::uuid"""
            + (includeDisabled ? "" : "\n                        AND enabled = true")
            + ";";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().valueAsUuid());
    params.add(clientIdentifier.valueAsUuid());

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
                        WHERE tenant_id = ?::uuid"""
            + (includeDisabled ? "" : "\n                        AND enabled = true")
            + "\n                        limit ?\n                        offset ?;";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(limit);
    params.add(offset);

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, ClientQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder where = new StringBuilder("WHERE tenant_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    if (queries.hasFrom()) {
      where.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      where.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasClientId()) {
      where.append(" AND id = ?::uuid");
      params.add(queries.clientId());
    }

    if (queries.hasClientIdAlias()) {
      where.append(" AND id_alias = ?");
      params.add(queries.clientIdAlias());
    }

    if (queries.hasClientName()) {
      where.append(" AND payload->>'client_name' ILIKE ?");
      params.add("%" + queries.clientName() + "%");
    }

    if (queries.hasClientUri()) {
      where.append(" AND payload->>'client_uri' ILIKE ?");
      params.add("%" + queries.clientUri() + "%");
    }

    if (queries.hasApplicationType()) {
      where.append(" AND payload->>'application_type' = ?");
      params.add(queries.applicationType());
    }

    if (queries.hasGrantTypes()) {
      where.append(" AND payload->'grant_types' @> ?::jsonb");
      params.add("[\"" + queries.grantTypes() + "\"]");
    }

    if (queries.hasResponseTypes()) {
      where.append(" AND payload->'response_types' @> ?::jsonb");
      params.add("[\"" + queries.responseTypes() + "\"]");
    }

    if (queries.hasTokenEndpointAuthMethod()) {
      where.append(" AND payload->>'token_endpoint_auth_method' = ?");
      params.add(queries.tokenEndpointAuthMethod());
    }

    if (queries.hasRedirectUris()) {
      where.append(" AND payload->'redirect_uris' @> ?::jsonb");
      params.add("[\"" + queries.redirectUris() + "\"]");
    }

    if (queries.hasScope()) {
      where.append(" AND payload->>'scope' ILIKE ?");
      params.add("%" + queries.scope() + "%");
    }

    if (queries.hasEnabled()) {
      where.append(" AND enabled = ?");
      params.add(queries.enabled());
    }

    if (queries.hasBackchannelTokenDeliveryMode()) {
      where.append(" AND payload->>'backchannel_token_delivery_mode' = ?");
      params.add(queries.backchannelTokenDeliveryMode());
    }

    if (queries.hasBackchannelUserCodeParameter()) {
      where.append(" AND payload->>'backchannel_user_code_parameter' = ?");
      params.add(queries.backchannelUserCodeParameter());
    }

    if (queries.hasAuthorizationDetailsTypes()) {
      where.append(" AND payload->'authorization_details_types' @> ?::jsonb");
      params.add("[\"" + queries.authorizationDetailsTypes() + "\"]");
    }

    if (queries.hasTlsClientAuthSubjectDn()) {
      where.append(" AND payload->>'tls_client_auth_subject_dn' = ?");
      params.add(queries.tlsClientAuthSubjectDn());
    }

    if (queries.hasTlsClientCertificateBoundAccessTokens()) {
      where.append(" AND payload->>'tls_client_certificate_bound_access_tokens' = ?");
      params.add(queries.tlsClientCertificateBoundAccessTokens());
    }

    if (queries.hasSoftwareId()) {
      where.append(" AND payload->>'software_id' = ?");
      params.add(queries.softwareId());
    }

    if (queries.hasSoftwareVersion()) {
      where.append(" AND payload->>'software_version' = ?");
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

    StringBuilder where = new StringBuilder("WHERE tenant_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    if (queries.hasFrom()) {
      where.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      where.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasClientId()) {
      where.append(" AND id = ?::uuid");
      params.add(queries.clientId());
    }

    if (queries.hasClientIdAlias()) {
      where.append(" AND id_alias = ?");
      params.add(queries.clientIdAlias());
    }

    if (queries.hasClientName()) {
      where.append(" AND payload->>'client_name' ILIKE ?");
      params.add("%" + queries.clientName() + "%");
    }

    if (queries.hasClientUri()) {
      where.append(" AND payload->>'client_uri' ILIKE ?");
      params.add("%" + queries.clientUri() + "%");
    }

    if (queries.hasApplicationType()) {
      where.append(" AND payload->>'application_type' = ?");
      params.add(queries.applicationType());
    }

    if (queries.hasGrantTypes()) {
      where.append(" AND payload->'grant_types' @> ?::jsonb");
      params.add("[\"" + queries.grantTypes() + "\"]");
    }

    if (queries.hasResponseTypes()) {
      where.append(" AND payload->'response_types' @> ?::jsonb");
      params.add("[\"" + queries.responseTypes() + "\"]");
    }

    if (queries.hasTokenEndpointAuthMethod()) {
      where.append(" AND payload->>'token_endpoint_auth_method' = ?");
      params.add(queries.tokenEndpointAuthMethod());
    }

    if (queries.hasRedirectUris()) {
      where.append(" AND payload->'redirect_uris' @> ?::jsonb");
      params.add("[\"" + queries.redirectUris() + "\"]");
    }

    if (queries.hasScope()) {
      where.append(" AND payload->>'scope' ILIKE ?");
      params.add("%" + queries.scope() + "%");
    }

    if (queries.hasEnabled()) {
      where.append(" AND enabled = ?");
      params.add(queries.enabled());
    }

    if (queries.hasBackchannelTokenDeliveryMode()) {
      where.append(" AND payload->>'backchannel_token_delivery_mode' = ?");
      params.add(queries.backchannelTokenDeliveryMode());
    }

    if (queries.hasBackchannelUserCodeParameter()) {
      where.append(" AND payload->>'backchannel_user_code_parameter' = ?");
      params.add(queries.backchannelUserCodeParameter());
    }

    if (queries.hasAuthorizationDetailsTypes()) {
      where.append(" AND payload->'authorization_details_types' @> ?::jsonb");
      params.add("[\"" + queries.authorizationDetailsTypes() + "\"]");
    }

    if (queries.hasTlsClientAuthSubjectDn()) {
      where.append(" AND payload->>'tls_client_auth_subject_dn' = ?");
      params.add(queries.tlsClientAuthSubjectDn());
    }

    if (queries.hasTlsClientCertificateBoundAccessTokens()) {
      where.append(" AND payload->>'tls_client_certificate_bound_access_tokens' = ?");
      params.add(queries.tlsClientCertificateBoundAccessTokens());
    }

    if (queries.hasSoftwareId()) {
      where.append(" AND payload->>'software_id' = ?");
      params.add(queries.softwareId());
    }

    if (queries.hasSoftwareVersion()) {
      where.append(" AND payload->>'software_version' = ?");
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
                payload = ?::jsonb
                WHERE tenant_id = ?::uuid
                AND id = ?::uuid
                """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientIdAlias());
    params.add(payload);
    params.add(tenant.identifierUUID());
    params.add(clientConfiguration.clientIdentifier().valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, RequestedClientId requestedClientId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                DELETE FROM client_configuration
                WHERE tenant_id = ?::uuid
                AND id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(requestedClientId.value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
