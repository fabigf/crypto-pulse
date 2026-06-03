package com.cryptopulse.wallet.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class WalletSchemaInitializerTest {

    @Test
    void shouldPatchLegacySchemaWhenStatusColumnIsMissing() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false, true, true, true, true, true, true);
        when(connection.createStatement()).thenReturn(statement);

        WalletSchemaInitializer initializer = new WalletSchemaInitializer(dataSource);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(statement).executeUpdate("ALTER TABLE balance_audit_record ADD COLUMN IF NOT EXISTS status VARCHAR(16)");
        verify(statement).executeUpdate("""
                UPDATE balance_audit_record
                SET status = CASE
                    WHEN closed_at IS NULL THEN 'RESERVED'
                    ELSE 'EXECUTED'
                END
                WHERE status IS NULL
                """);
    }

    @Test
    void shouldSkipPatchWhenStatusColumnAlreadyExists() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, true, true, true, true);
        when(connection.createStatement()).thenReturn(statement);

        WalletSchemaInitializer initializer = new WalletSchemaInitializer(dataSource);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(statement, never()).executeUpdate(anyString());
    }

    @Test
    void shouldPatchProcessedExecutionSchemaWhenExecutionColumnsAreMissing() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false, false, false, false, false, false);
        when(connection.createStatement()).thenReturn(statement);

        WalletSchemaInitializer initializer = new WalletSchemaInitializer(dataSource);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(statement).executeUpdate("ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS ticker VARCHAR(16)");
        verify(statement).executeUpdate("ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS side VARCHAR(16)");
        verify(statement).executeUpdate("ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS quantity NUMERIC(19,8)");
        verify(statement).executeUpdate(
                "ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS execution_price NUMERIC(19,8)"
        );
        verify(statement).executeUpdate("ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS total_cost NUMERIC(19,8)");
        verify(statement).executeUpdate(
                "ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS reserved_amount NUMERIC(19,8)"
        );
    }
}
