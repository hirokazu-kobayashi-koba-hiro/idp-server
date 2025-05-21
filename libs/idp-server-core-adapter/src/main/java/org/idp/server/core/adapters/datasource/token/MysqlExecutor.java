package org.idp.server.core.adapters.datasource.token;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements OAuthTokenSqlExecutor {

  @Override
  public void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                             INSERT INTO oauth_token (
                                id,
                                tenant_id,
                                token_issuer,
                                token_type,
                                encrypted_access_token,
                                hashed_access_token,
                                user_id,
                                user_payload,
                                authentication,
                                client_id,
                                client_payload,
                                grant_type,
                                scopes,
                                id_token_claims,
                                userinfo_claims,
                                custom_properties,
                                authorization_details,
                                expires_in,
                                access_token_expired_at,
                                access_token_created_at,
                                encrypted_refresh_token,
                                hashed_refresh_token,
                                refresh_token_expired_at,
                                refresh_token_created_at,
                                id_token,
                                client_certification_thumbprint,
                                c_nonce,
                                c_nonce_expires_in
                                )
                                VALUES (
                                ?::uuid,
                                ?::uuid,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?::uuid,
                                ?::jsonb,
                                ?::jsonb,
                                ?,
                                ?::jsonb,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?::jsonb,
                                ?::jsonb,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?
                                );
                              """;
    List<Object> params = InsertSqlParamsCreator.create(oAuthToken, aesCipher, hmacHasher);
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneByAccessToken(
      Tenant tenant,
      AccessTokenEntity accessTokenEntity,
      AesCipher aesCipher,
      HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
        FROM oauth_token
        WHERE tenant_id = ? AND hashed_access_token = ?;
        """;

    List<Object> params =
        List.of(tenant.identifierValue(), hmacHasher.hash(accessTokenEntity.value()));
    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneByRefreshToken(
      Tenant tenant,
      RefreshTokenEntity refreshTokenEntity,
      AesCipher aesCipher,
      HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            FROM oauth_token
            WHERE tenant_id = ? AND hashed_refresh_token = ?;
            """;

    List<Object> params =
        List.of(tenant.identifierValue(), hmacHasher.hash(refreshTokenEntity.value()));
    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public void delete(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate = """
            DELETE FROM oauth_token WHERE id = ?;
            """;
    List<Object> params = List.of(oAuthToken.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  String selectSql =
      """
               SELECT
               id,
               tenant_id,
               token_issuer,
               token_type,
               encrypted_access_token,
               hashed_access_token,
               user_id,
               user_payload,
               authentication,
               client_id,
               client_payload,
               grant_type,
               scopes,
               id_token_claims,
               userinfo_claims,
               custom_properties,
               authorization_details,
               expires_in,
               access_token_expired_at,
               access_token_created_at,
               encrypted_refresh_token,
               hashed_refresh_token,
               refresh_token_expired_at,
               refresh_token_created_at,
               id_token,
               client_certification_thumbprint,
               c_nonce,
               c_nonce_expires_in \n
               """;
}
