package dk.nykredit.resilience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.InjectionPoint;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Test for {@link ResilientExecutorProducer}
 */
@RunWith(MockitoJUnitRunner.class)
public class ResilientExecutorProducerTest {

    @Mock
    private InjectionPoint ip;

    @Mock
    private Annotated annotated;

    @Mock
    private ManagedScheduledExecutorService executor;

    private ResilientExecutorProducer producer;

    @Before
    public void setup() {
        when(ip.getAnnotated()).thenReturn(annotated);
        producer = new ResilientExecutorProducer() {
            @Override
            ManagedScheduledExecutorService getExecutor(String managedScheduledExecutorServic) {
                return executor;
            }
        };
    }

    @Test
    public void testProducesDefault() {
        ResilientExecutor re = producer.createResilientExecutor(ip);
        assertNotNull("Using producer without annotation should create executor", re);
        assertEquals("Using producer without annotation should create executor with default strategy", PolynomialBackoffStrategy.class,
                re.getStrategy().getClass());
    }

    @Test
    public void testProduces() {
        Resilient conf = mock(Resilient.class);
        when(conf.strategy()).thenAnswer((InvocationOnMock invocation) -> TestBackoffStrategy.class);
        when(annotated.getAnnotation(Resilient.class)).thenReturn(conf);
        ResilientExecutor re = producer.createResilientExecutor(ip);
        assertNotNull("Using producere with annotation should create executor", re);
        assertEquals("Using producere with annotation should create executor with strategy", TestBackoffStrategy.class,
                re.getStrategy().getClass());
    }

    @Test(expected = ResilientExecutorException.class)
    public void testGetExecutorWithNull() {
        ResilientExecutorProducer rep = new ResilientExecutorProducer();
        rep.getExecutor(null);
    }

    @Test(expected = ResilientExecutorException.class)
    public void testGetExecutorEmptyName() {
        ResilientExecutorProducer rep = new ResilientExecutorProducer();
        rep.getExecutor("");
    }

    @Test(expected = ResilientExecutorException.class)
    public void testGetExecutorNonitialContext() {
        ResilientExecutorProducer rep = new ResilientExecutorProducer();
        rep.getExecutor(ResilientExecutor.DEFAULT_SCHEDULED_EXECUTOR_SERVICE);
    }

    public static class TestBackoffStrategy implements BackOffStrategy {

        @Override
        public int delay() {
            return 1;
        }

        @Override
        public int getMaxRetries() {
            return 1;
        }

        @Override
        public void incrementFailures() {
            //Do nothing...
        }

        @Override
        public int failures() {
            return 0;
        }

        @Override
        public void resetFailures() {
            //Do nothing...
        }

        @Override
        public boolean hasRetriesBeenExceeded() {
            return false;
        }

        @Override
        public TimeUnit timeUnit() {
            return TimeUnit.SECONDS;
        }

        @Override
        public void configure(InjectionPoint ip) {
            //Do Nothing...
        }

    }

}
