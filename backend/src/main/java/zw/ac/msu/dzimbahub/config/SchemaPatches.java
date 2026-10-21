package zw.ac.msu.dzimbahub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * Small one-off database fixes that ddl-auto=update cannot do by itself.
 *
 * Hibernate created a CHECK constraint on properties.listing_status that only allows the old values
 * (ACTIVE, SUSPENDED). ddl-auto=update never changes existing constraints, so the new PENDING and
 * REJECTED values would be refused. This drops that old constraint on PostgreSQL. It is safe to run on
 * every start: once the constraint is gone there is nothing left to drop.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaPatches implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaPatches.class);
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public SchemaPatches(JdbcTemplate jdbc, DataSource dataSource) { this.jdbc = jdbc; this.dataSource = dataSource; }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            if (!c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) return;
        } catch (Exception e) {
            log.warn("Could not check the database type: {}", e.getMessage());
            return;
        }
        dropEnumCheck("properties", "listing_status");
    }

    private void dropEnumCheck(String table, String column) {
        try {
            List<String> names = jdbc.queryForList(
                    "SELECT conname FROM pg_constraint WHERE conrelid = to_regclass(?) AND contype = 'c' "
                            + "AND pg_get_constraintdef(oid) LIKE ?", String.class, table, "%" + column + "%");
            for (String name : names) {
                jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS \"" + name.replace("\"", "") + "\"");
                log.info("Removed old check constraint {} on {}.{}", name, table, column);
            }
        } catch (Exception e) {
            log.warn("Could not update the {}.{} constraint: {}", table, column, e.getMessage());
        }
    }
}
