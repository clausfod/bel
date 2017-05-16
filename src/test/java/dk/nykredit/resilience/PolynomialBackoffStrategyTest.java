package dk.nykredit.resilience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.InjectionPoint;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Test for {@link PolynomialBackoffStrategy}
 */
@RunWith(MockitoJUnitRunner.class)
public class PolynomialBackoffStrategyTest {

    @Mock
    InjectionPoint ip;

    @Mock
    Annotated annotated;

    @Before
    public void setup() {
        when(ip.getAnnotated()).thenReturn(annotated);
    }

    @Test
    public void testBackOffNoConfig() {
        PolynomialBackoffStrategy s = new PolynomialBackoffStrategy();
        s.configure(ip);
        s.incrementFailures();
        s.incrementFailures();
        assertEquals(2, s.delay());
        s.incrementFailures();
        assertEquals(4, s.delay());
    }

    @Test
    public void testBackOffWithconfig() {
        PolynomialBackoffStrategyConfig conf = mock(PolynomialBackoffStrategyConfig.class);
        when(conf.delay()).thenReturn(3);
        when(conf.maxDelay()).thenReturn(20);
        when(conf.retries()).thenReturn(5);
        when(annotated.getAnnotation(PolynomialBackoffStrategyConfig.class)).thenReturn(conf);
        PolynomialBackoffStrategy s = new PolynomialBackoffStrategy();
        s.configure(ip);
        assertFalse(s.hasRetriesBeenExceeded());
        s.incrementFailures();
        s.incrementFailures();
        assertEquals(6, s.delay());
        s.incrementFailures();
        assertEquals(12, s.delay());
        s.incrementFailures();
        assertEquals(20, s.delay());
        assertFalse(s.hasRetriesBeenExceeded());
        s.incrementFailures();
        assertTrue(s.hasRetriesBeenExceeded());
    }
}
