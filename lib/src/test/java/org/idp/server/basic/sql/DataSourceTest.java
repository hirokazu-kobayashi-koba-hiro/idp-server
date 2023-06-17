package org.idp.server.basic.sql;

import java.util.UUID;
import org.idp.server.handler.oauth.datasource.database.code.AuthorizationCodeGrantDataSource;
import org.idp.server.handler.oauth.datasource.database.request.AuthorizationRequestDataSource;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.junit.jupiter.api.Test;

public class DataSourceTest {

  SqlConnection sqlConnection =
      new SqlConnection("jdbc:postgresql://localhost:5432/idpserver", "idpserver", "idpserver");
  AuthorizationRequestDataSource authorizationRequestDataSource =
      new AuthorizationRequestDataSource(sqlConnection);
  AuthorizationCodeGrantDataSource authorizationCodeGrantDataSource =
      new AuthorizationCodeGrantDataSource(sqlConnection);

  @Test
  void register() {
    AuthorizationRequest request =
        new AuthorizationRequestBuilder()
            .add(new AuthorizationRequestIdentifier(UUID.randomUUID().toString()))
            .build();
    authorizationRequestDataSource.register(request);
    authorizationCodeGrantDataSource.register(new AuthorizationCodeGrant());
  }
}
