package org.idp.server.core.adapters.datasource.config;

import com.zaxxer.hikari.HikariDataSource;
import org.idp.server.core.basic.datasource.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class HikariConnectionProvider implements DbConnectionProvider {

    Map<DatabaseType, HikariDataSource> writerConfigs;
    Map<DatabaseType, HikariDataSource> readerConfigs;

    public HikariConnectionProvider(DatabaseConfig databaseConfig) {
        this.writerConfigs = HikariDataSourceFactory.create(databaseConfig.writerConfigs());
        this.readerConfigs = HikariDataSourceFactory.create(databaseConfig.readerConfigs());
    }


    public Connection getConnection(DatabaseType databaseType) {
        OperationType type = OperationContext.get();
        HikariDataSource hikariDataSource =
                (type == OperationType.READ)
                        ? readerConfigs.get(databaseType)
                        : writerConfigs.get(databaseType);
        try {
            Connection connection = hikariDataSource.getConnection();
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            throw new SqlRuntimeException("Failed to get DB connection", e);
        }
    }
}
