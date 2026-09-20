package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.SharedPostgresContainer;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared base for every {@code @DataJpaTest} repository test. Extends {@link SharedPostgresContainer}
 * for the container itself (see its javadoc for why that's a manually-started singleton rather
 * than a {@code @Testcontainers}-managed one). Every subclass sees byte-identical test
 * configuration, so Spring's {@code TestContext} cache reuses a single context across all of them
 * instead of spinning up one per class.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} is required: {@code @DataJpaTest}
 * normally swaps in an embedded database, which would both bypass the real Postgres container
 * and skip the Flyway-owned schema entirely.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class AbstractRepositoryTest extends SharedPostgresContainer {

    protected static final String NOT_NULL_VIOLATION = "23502";
    protected static final String FOREIGN_KEY_VIOLATION = "23503";
    protected static final String UNIQUE_VIOLATION = "23505";
    protected static final String CHECK_VIOLATION = "23514";

    @Autowired
    private DataSource dataSource;

    /**
     * Proves a specific DB-level constraint failed &mdash; not merely "some {@code SQLException}
     * was thrown" &mdash; by asserting both the SQLSTATE class (see the constants above) and the
     * exact identifier Postgres reports: the constraint name for a CHECK/FOREIGN KEY/UNIQUE
     * violation, or the column name for a NOT NULL violation (Postgres tracks NOT NULL as
     * {@code attnotnull} on the column, not as a named {@code pg_constraint} row, so there is no
     * constraint name to report for it).
     *
     * <p>Runs on its own JDBC connection with autocommit off, rolled back in a {@code finally}
     * regardless of outcome &mdash; deliberately <em>not</em> routed through
     * {@code TestEntityManager}'s JPA {@code EntityManager} (a caught constraint violation marks
     * that Hibernate session rollback-only per the JPA spec, and running these checks through the
     * same session/transaction other tests in the class share was found to leak a pooled
     * connection). The explicit rollback is also what makes {@code setupSql} safe: it runs on the
     * <em>same</em> connection and transaction as {@code violatingSql}, so a fixture row a setup
     * statement inserts is visible to the violating statement even though neither one is ever
     * committed &mdash; sidestepping the separate problem of a JPA {@code @DataJpaTest} fixture
     * created in the test's own (uncommitted, rolled-back-at-the-end) transaction being invisible
     * to a <em>different</em> connection, which would otherwise make a test intending to prove one
     * constraint (e.g. {@code chk_payment_amount_positive}) actually fail on an unrelated one
     * (the FK to a row the other connection can't see) for the wrong reason.
     */
    protected void assertConstraintViolation(List<String> setupSql, String violatingSql,
                                              String expectedSqlState, String expectedIdentifier) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : setupSql) {
                    statement.executeUpdate(sql);
                }
                SQLException thrown = assertThrows(SQLException.class, () -> statement.executeUpdate(violatingSql));
                assertEquals(expectedSqlState, thrown.getSQLState(), () ->
                        "expected SQLSTATE " + expectedSqlState + " but was " + thrown.getSQLState()
                                + ": " + thrown.getMessage());
                if (!(thrown instanceof PSQLException psqlException)) {
                    fail("expected a PSQLException carrying constraint/column detail, got: " + thrown);
                    return;
                }
                ServerErrorMessage serverError = psqlException.getServerErrorMessage();
                if (serverError == null) {
                    fail("PSQLException carried no server error detail: " + thrown);
                    return;
                }
                String actualIdentifier = serverError.getConstraint() != null
                        ? serverError.getConstraint()
                        : serverError.getColumn();
                assertEquals(expectedIdentifier, actualIdentifier, () ->
                        "expected constraint/column '" + expectedIdentifier + "' but was '" + actualIdentifier
                                + "': " + thrown.getMessage());
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not obtain a JDBC connection for the constraint check", e);
        }
    }

    /** Overload for the common case with no fixture rows to set up on the same connection. */
    protected void assertConstraintViolation(String violatingSql, String expectedSqlState, String expectedIdentifier) {
        assertConstraintViolation(List.of(), violatingSql, expectedSqlState, expectedIdentifier);
    }
}
