package vn.englishconversation.ai.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.common.exception.ApiException;

/** Serializes quota reservation across instances until its transaction commits. */
@Service
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class AiAdmissionService {
  private final DataSource dataSource;
  private final TransactionTemplate transaction;

  public AiAdmissionService(DataSource dataSource, PlatformTransactionManager manager) {
    this.dataSource = dataSource;
    transaction = new TransactionTemplate(manager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  // Keep the named lock through commit/rollback on the transaction's own connection.
  public <T> T reserve(Supplier<T> reservation) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Admission must start outside an existing transaction");
    var event = new AdmissionEvent();
    long started = System.nanoTime();
    event.begin();
    try {
      synchronized (this) {
        event.monitorWaitNanos = System.nanoTime() - started;
        return reserveLocked(reservation, event);
      }
    } catch (RuntimeException | Error failure) {
      event.outcome = failure instanceof ApiException api ? api.code : "RESERVATION_FAILED";
      throw failure;
    } finally {
      event.reservationNanos = System.nanoTime() - started;
      event.end();
      event.commit();
    }
  }

  private <T> T reserveLocked(Supplier<T> reservation, AdmissionEvent event) {
    var lock = new ReservationLock(event);
    long connectionStarted = System.nanoTime();
    try {
      T result = transaction.execute(status -> {
        if (!TransactionSynchronizationManager.hasResource(dataSource))
          throw new IllegalStateException("Transaction manager must bind the admission datasource");
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
          if (!DataSourceUtils.isConnectionTransactional(connection, dataSource)
              || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Admission requires a transaction-bound JDBC connection");
          lock.connection = connection;
          // Register before GET_LOCK: even a ResultSet/statement close failure must release it.
          TransactionSynchronizationManager.registerSynchronization(lock);
          event.connectionWaitNanos = System.nanoTime() - connectionStarted;
          lock.acquire();
          return reservation.get();
        } finally {
          // Decrements the Spring resource reference; the manager still owns the physical connection.
          DataSourceUtils.releaseConnection(connection, dataSource);
        }
      });
      if (lock.abortFailed) throw unavailable();
      return result;
    } catch (CannotCreateTransactionException | CannotGetJdbcConnectionException failure) {
      if (event.connectionWaitNanos == 0)
        event.connectionWaitNanos = System.nanoTime() - connectionStarted;
      throw unavailable();
    }
  }

  private static ApiException unavailable() {
    return new ApiException(503, "ADMISSION_UNAVAILABLE", "Chưa thể nhận tác vụ. Hãy thử lại.");
  }

  /** Spring invokes afterCompletion after the DB outcome, before returning the connection to its pool. */
  private static final class ReservationLock implements TransactionSynchronization {
    private static final String LOCK_NAME = "CONCAT('practice:',LEFT(SHA2(DATABASE(),256),48))";
    private final AdmissionEvent event;
    private Connection connection;
    private boolean acquired, abortFailed;
    private long acquiredAt;

    ReservationLock(AdmissionEvent event) { this.event = event; }

    void acquire() {
      long lockStarted = System.nanoTime();
      try (var statement = connection.prepareStatement("SELECT GET_LOCK(" + LOCK_NAME + ",2)");
          var result = statement.executeQuery()) {
        if (!result.next() || result.getInt(1) != 1)
          throw new ApiException(503, "ADMISSION_BUSY", "Hệ thống đang bận. Hãy thử lại.");
        acquired = true;
        acquiredAt = System.nanoTime();
      } catch (SQLException failure) {
        throw unavailable();
      } finally {
        event.lockWaitNanos = System.nanoTime() - lockStarted;
      }
    }

    @Override
    public void afterCompletion(int status) {
      if (!acquired) return;
      try {
        try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(" + LOCK_NAME + ")");
            var result = release.executeQuery()) {
          if (!result.next() || result.getInt(1) != 1) connection.abort(Runnable::run);
        } catch (SQLException failure) {
          connection.abort(Runnable::run);
        }
      } catch (SQLException failure) {
        // afterCompletion errors do not propagate from Spring. Preserve the admission failure signal.
        abortFailed = true;
      } finally {
        event.lockHoldNanos = System.nanoTime() - acquiredAt;
      }
    }
  }
}
