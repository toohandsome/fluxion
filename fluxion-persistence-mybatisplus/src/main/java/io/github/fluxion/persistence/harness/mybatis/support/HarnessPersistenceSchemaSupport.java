package io.github.fluxion.persistence.harness.mybatis.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

public final class HarnessPersistenceSchemaSupport {

    private static final String[] DDL = {
            """
            create table if not exists flow_instances (
                instance_id bigint primary key,
                flow_code varchar(255) not null,
                status varchar(64) not null,
                error_code varchar(255),
                error_message varchar(2000),
                flow_output_json clob,
                duration_ms bigint not null
            )
            """,
            """
            create table if not exists node_executions (
                instance_id bigint not null,
                node_id varchar(255) not null,
                node_type varchar(128) not null,
                status varchar(64) not null,
                duration_ms bigint not null,
                attempt_count int not null,
                skip_reason varchar(255),
                error_code varchar(255),
                error_message varchar(2000),
                output_json clob,
                primary key (instance_id, node_id)
            )
            """,
            """
            create table if not exists node_execution_attempts (
                instance_id bigint not null,
                node_id varchar(255) not null,
                attempt_no int not null,
                status varchar(64) not null,
                duration_ms bigint not null,
                error_code varchar(255),
                error_message varchar(2000),
                primary key (instance_id, node_id, attempt_no)
            )
            """
    };

    private HarnessPersistenceSchemaSupport() {
    }

    public static void initialize(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.execute(ddl);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize harness persistence schema", exception);
        }
    }
}
