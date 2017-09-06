package dk.nykredit.resilience;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ejb.ApplicationException;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.transaction.UserTransaction;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

/**
 * Test for {@link ResilientExecutor}.
 */
@RunWith(MockitoJUnitRunner.class)
public class ResilientExecutorTest {

    @Mock
    private ManagedScheduledExecutorService service;

    @Mock
    private UserTransaction ut;

    private ResilientExecutor executor;

    private PolynomialBackoffStrategy strategy;

    @Before
    public void setup() {
        strategy = new PolynomialBackoffStrategy();
        executor = new TestResilientExecutor(strategy, service, ut);
        when(service.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }

        });
    }

    @Test
    public void testExecute() throws Exception {
        executor.execute(() -> {
        });
        verify(ut).begin();
        verify(ut).commit();
    }

    @Test
    public void testExecuteRetryRuntimeException() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        executor.execute(() -> {
            if (count.get() == 0) {
                count.addAndGet(1);
                throw new RuntimeException("Test");
            }
        });
        verify(ut, times(2)).begin();
        verify(ut).rollback();
        verify(ut).commit();
    }

    @Test
    public void testExecuteRetryApplicationException() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        executor.execute(() -> {
            if (count.get() == 0) {
                count.addAndGet(1);
                throw new TestApplicationException();
            }
        });
        verify(ut, times(2)).begin();
        verify(ut, times(2)).commit();
    }

    @Test
    public void testExecuteRetryInheritedApplicationException() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        executor.execute(() -> {
            if (count.get() == 0) {
                count.addAndGet(1);
                throw new TestInheritedApplicationException();
            }
        });
        verify(ut, times(2)).begin();
        verify(ut, times(2)).commit();
    }

    @Test
    public void testMaxRetriesExeced() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        executor.execute(() -> {
            count.incrementAndGet();
            throw new RuntimeException("Test");
        }, e -> {
            errors.incrementAndGet();
        });
        assertEquals("After a default of 10 retries execution should stop", 10, count.get());
        assertEquals("When retries has been exceeded error code should run once", 1, errors.get());
    }

    @Test
    public void testExceptionInFailureCode() throws Exception {
        executor.execute(() -> {
            throw new RuntimeException("Test");
        }, e -> {
            throw new RuntimeException("Test");
        });
        verify(ut, times(11)).rollback();
        verify(ut, never()).commit();
    }

    @Test
    public void testNullTransaction() {
        executor = new TestResilientExecutor(strategy, service, null);
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        executor.execute(() -> {
            count.incrementAndGet();
        }, e -> {
            errors.incrementAndGet();
        });
        assertEquals(0, count.get());
        assertEquals(0, errors.get());
    }

    private static class TestResilientExecutor extends ResilientExecutor {

        private UserTransaction ut;

        public TestResilientExecutor(BackOffStrategy strategy, ManagedScheduledExecutorService executor, UserTransaction ut) {
            super(strategy, executor);
            this.ut = ut;
        }

        @Override
        protected UserTransaction getUserTransaction() {
            return ut;
        }
    }

    @ApplicationException(rollback = false)
    public static class TestApplicationException extends RuntimeException {

        private static final long serialVersionUID = -561561554937735049L;

    }

    @ApplicationException(rollback = false, inherited = true)
    public static class TestSuperSuperApplicationException extends RuntimeException {

        private static final long serialVersionUID = 5511558005180615995L;

    }

    public static class TestSuperApplicationException extends TestSuperSuperApplicationException {

        private static final long serialVersionUID = 3064382691348889837L;

    }

    public static class TestInheritedApplicationException extends TestSuperApplicationException {

        private static final long serialVersionUID = 5511558005180615995L;

    }

}
