package org.idp.server.core.handler.ciba.datasource.database.grant;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.type.ciba.AuthReqId;

public class CibaGrantDataSource implements CibaGrantRepository {

  @Override
  public void register(CibaGrant cibaGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sql = SqlCreator.createInsert(cibaGrant);
    sqlExecutor.execute(sql);
  }

  @Override
  public void update(CibaGrant cibaGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sql = SqlCreator.crateUpdate(cibaGrant);
    sqlExecutor.execute(sql);
  }

  @Override
  public CibaGrant find(AuthReqId authReqId) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            SELECT backchannel_authentication_request_id, auth_req_id, expired_at, interval, status, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details
            FROM ciba_grant
            WHERE auth_req_id = '%s';
            """;
    String sql = String.format(sqlTemplate, authReqId.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);
    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new CibaGrant();
    }
    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(CibaGrant cibaGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            DELETE FROM ciba_grant WHERE backchannel_authentication_request_id = '%s';
            """;
    String sql =
        String.format(sqlTemplate, cibaGrant.backchannelAuthenticationRequestIdentifier().value());
    sqlExecutor.execute(sql);
  }
}
