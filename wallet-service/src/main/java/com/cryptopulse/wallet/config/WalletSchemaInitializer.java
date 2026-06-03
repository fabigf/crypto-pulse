package com.cryptopulse.wallet.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class WalletSchemaInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(WalletSchemaInitializer.class);
    private static final String ADD_STATUS_COLUMN_SQL =
            "ALTER TABLE balance_audit_record ADD COLUMN IF NOT EXISTS status VARCHAR(16)";
    private static final String BACKFILL_STATUS_SQL = """
            UPDATE balance_audit_record
            SET status = CASE
                WHEN closed_at IS NULL THEN 'RESERVED'
                ELSE 'EXECUTED'
            END
            WHERE status IS NULL
            """;
    private static final Map<String, String> PROCESSED_EXECUTION_COMPAT_COLUMNS = new LinkedHashMap<>();

    static {
        PROCESSED_EXECUTION_COMPAT_COLUMNS.put("ticker", "ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS ticker VARCHAR(16)");
        PROCESSED_EXECUTION_COMPAT_COLUMNS.put("side", "ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS side VARCHAR(16)");
        PROCESSED_EXECUTION_COMPAT_COLUMNS.put("quantity", "ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS quantity NUMERIC(19,8)");
        PROCESSED_EXECUTION_COMPAT_COLUMNS.put(
                "execution_price",
                "ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS execution_price NUMERIC(19,8)"
        );
        PROCESSED_EXECUTION_COMPAT_COLUMNS.put(
                "total_cost",
                "ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS total_cost NUMERIC(19,8)"
        );
        PROCESSED_EXECUTION_COMPAT_COLUMNS.put(
                "reserved_amount",
                "ALTER TABLE processed_execution_record ADD COLUMN IF NOT EXISTS reserved_amount NUMERIC(19,8)"
        );
    }

    private final DataSource dataSource;

    public WalletSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            patchLegacyBalanceAuditSchema(connection);
            patchProcessedExecutionSchema(connection);
        }
    }

    private void patchLegacyBalanceAuditSchema(Connection connection) throws Exception {
        if (hasColumn(connection, "balance_audit_record", "status")) {
            return;
        }

        LOGGER.warn("Legacy wallet schema detected without balance_audit_record.status; applying compatibility patch");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ADD_STATUS_COLUMN_SQL);
            statement.executeUpdate(BACKFILL_STATUS_SQL);
        }
    }

    private void patchProcessedExecutionSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            for (Map.Entry<String, String> compatColumn : PROCESSED_EXECUTION_COMPAT_COLUMNS.entrySet()) {
                if (hasColumn(connection, "processed_execution_record", compatColumn.getKey())) {
                    continue;
                }

                LOGGER.warn(
                        "Legacy wallet schema detected without processed_execution_record.{}; applying compatibility patch",
                        compatColumn.getKey()
                );
                statement.executeUpdate(compatColumn.getValue());
            }
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                select 1
                from information_schema.columns
                where lower(table_name) = lower(?)
                  and lower(column_name) = lower(?)
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
