package vn.englishconversation.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import vn.englishconversation.common.exception.ApiException;

class AiAdmissionServiceTest {
  @Test
  void releasesLockOnlyAfterReservationCommits() throws Exception {
    var fixture = new Fixture(1);
    assertEquals("reserved", fixture.service.reserve(() -> {
      var bound = DataSourceUtils.getConnection(fixture.source);
      assertSame(fixture.connection, bound);
      assertTrue(DataSourceUtils.isConnectionTransactional(bound, fixture.source));
      DataSourceUtils.releaseConnection(bound, fixture.source);
      return "reserved";
    }));
    var order = inOrder(fixture.connection, fixture.release);
    order.verify(fixture.connection).commit();
    order.verify(fixture.release).executeQuery();
    order.verify(fixture.connection).close();
    verify(fixture.source, times(1)).getConnection();
  }

  @Test
  void rollsBackAndReleasesLockWhenReservationFails() throws Exception {
    var fixture = new Fixture(1);
    var failure = new IllegalStateException("synthetic reservation failure");
    assertSame(failure, assertThrows(IllegalStateException.class,
        () -> fixture.service.reserve(() -> { throw failure; })));
    var order = inOrder(fixture.connection, fixture.release);
    order.verify(fixture.connection).rollback();
    order.verify(fixture.release).executeQuery();
    order.verify(fixture.connection).close();
  }

  @Test
  void lockTimeoutRollsBackWithoutRunningReservation() throws Exception {
    var fixture = new Fixture(0);
    assertEquals("ADMISSION_BUSY", assertThrows(ApiException.class,
        () -> fixture.service.reserve(() -> fail("must not reserve"))).code);
    verify(fixture.connection).rollback();
    verify(fixture.connection, never()).commit();
    verify(fixture.release, never()).executeQuery();
    verify(fixture.connection).close();
  }

  @Test
  void commitFailureStillReleasesLockBeforeReturningConnection() throws Exception {
    var fixture = new Fixture(1);
    doThrow(new SQLException("Synthetic commit failure")).when(fixture.connection).commit();
    assertThrows(TransactionException.class, () -> fixture.service.reserve(() -> "reserved"));
    var order = inOrder(fixture.connection, fixture.release);
    order.verify(fixture.connection).commit();
    order.verify(fixture.release).executeQuery();
    order.verify(fixture.connection).close();
  }

  @Test
  void failedReleaseAbortsConnectionBeforeReturningIt() throws Exception {
    var fixture = new Fixture(1);
    when(fixture.release.executeQuery()).thenThrow(new SQLException("Synthetic release failure"));
    assertEquals("reserved", fixture.service.reserve(() -> "reserved"));
    var order = inOrder(fixture.connection, fixture.release);
    order.verify(fixture.connection).commit();
    order.verify(fixture.release).executeQuery();
    order.verify(fixture.connection).abort(any());
    order.verify(fixture.connection).close();
  }

  @Test
  void abortFailureDoesNotReportSuccessAfterCommit() throws Exception {
    var fixture = new Fixture(1);
    when(fixture.release.executeQuery()).thenThrow(new SQLException("Synthetic release failure"));
    doThrow(new SQLException("Synthetic abort failure")).when(fixture.connection).abort(any());
    assertEquals("ADMISSION_UNAVAILABLE", assertThrows(ApiException.class,
        () -> fixture.service.reserve(() -> "reserved")).code);
    verify(fixture.connection).commit();
    verify(fixture.connection).close();
  }

  @Test
  void acquiredLockIsReleasedEvenWhenAcquisitionResultCloseFails() throws Exception {
    var fixture = new Fixture(1);
    doThrow(new SQLException("Synthetic result close failure")).when(fixture.acquisition).close();
    assertEquals("ADMISSION_UNAVAILABLE", assertThrows(ApiException.class,
        () -> fixture.service.reserve(() -> fail("must not reserve"))).code);
    var order = inOrder(fixture.connection, fixture.release);
    order.verify(fixture.connection).rollback();
    order.verify(fixture.release).executeQuery();
    order.verify(fixture.connection).close();
  }

  @Test
  void rejectsAnExistingTransactionBeforeAcquiringNamedLock() throws Exception {
    var fixture = new Fixture(1);
    new TransactionTemplate(fixture.manager).execute(ignored -> {
      assertThrows(IllegalStateException.class, () -> fixture.service.reserve(() -> fail("must not reserve")));
      return null;
    });
    verify(fixture.source, times(1)).getConnection();
    verify(fixture.acquire, never()).executeQuery();
  }

  @Test
  void rejectsATransactionManagerBoundToAnotherDatasource() throws Exception {
    var fixture = new Fixture(1);
    var other = mock(DataSource.class);
    when(other.getConnection()).thenReturn(fixture.connection);
    var mismatched = new AiAdmissionService(fixture.source, new DataSourceTransactionManager(other));
    assertThrows(IllegalStateException.class, () -> mismatched.reserve(() -> fail("must not reserve")));
    verify(fixture.source, never()).getConnection();
    verify(fixture.acquire, never()).executeQuery();
    verify(fixture.connection).rollback();
  }

  @Test
  void connectionExhaustionRejectsBeforeCallbackAndNamedLock() throws Exception {
    var fixture = new Fixture(1);
    when(fixture.source.getConnection()).thenThrow(new SQLException("Synthetic pool exhausted"));
    assertEquals("ADMISSION_UNAVAILABLE", assertThrows(ApiException.class,
        () -> fixture.service.reserve(() -> fail("must not reserve"))).code);
    verify(fixture.acquire, never()).executeQuery();
    verify(fixture.release, never()).executeQuery();
  }

  private static class Fixture {
    final DataSource source = mock(DataSource.class);
    final Connection connection = mock(Connection.class);
    final PreparedStatement acquire = mock(PreparedStatement.class), release = mock(PreparedStatement.class);
    final ResultSet acquisition = mock(ResultSet.class);
    final DataSourceTransactionManager manager;
    final AiAdmissionService service;

    Fixture(int acquired) throws Exception {
      var released = mock(ResultSet.class);
      when(source.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(true);
      when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
      when(connection.prepareStatement(startsWith("SELECT GET_LOCK"))).thenReturn(acquire);
      when(connection.prepareStatement(startsWith("SELECT RELEASE_LOCK"))).thenReturn(release);
      when(acquire.executeQuery()).thenReturn(acquisition);
      when(acquisition.next()).thenReturn(true);
      when(acquisition.getInt(1)).thenReturn(acquired);
      when(release.executeQuery()).thenReturn(released);
      when(released.next()).thenReturn(true);
      when(released.getInt(1)).thenReturn(1);
      manager = new DataSourceTransactionManager(source);
      service = new AiAdmissionService(source, manager);
    }
  }
}
