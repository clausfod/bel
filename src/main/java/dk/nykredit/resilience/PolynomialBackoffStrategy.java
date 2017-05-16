package dk.nykredit.resilience;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.inject.spi.InjectionPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polynomial back-off strategy for the {@link dk.nykredit.resilience.ResilientExecutor}.
 */
public class PolynomialBackoffStrategy implements BackOffStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolynomialBackoffStrategy.class);

    private static final int DEFAULT_MAX_RETRIES = 10;
    private static final int DEFAULT_MAX_DELAY = 10;
    private static final int DEFAULT_DELAY = 1;
    private final AtomicInteger failures = new AtomicInteger(0);
    private int delay = DEFAULT_DELAY;
    private int maxDelay = DEFAULT_MAX_DELAY;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private TimeUnit timeUnit = TimeUnit.SECONDS;

    @Override
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Configure the polynomial back-off strategy using {@link dk.nykredit.resilience.PolynomialBackoffStrategyConfig} annotation.
     *
     * @param ip the injection point of the executor
     */
    @Override
    public void configure(InjectionPoint ip) {
        Optional<PolynomialBackoffStrategyConfig> conf = Optional
                .ofNullable(ip.getAnnotated().getAnnotation(PolynomialBackoffStrategyConfig.class));
        conf.map(PolynomialBackoffStrategyConfig::delay).filter(d -> d >= 1).ifPresent(d -> this.delay = d);
        conf.map(PolynomialBackoffStrategyConfig::maxDelay).ifPresent(m -> this.maxDelay = m);
        conf.map(PolynomialBackoffStrategyConfig::retries).ifPresent(r -> this.maxRetries = r);
        conf.map(PolynomialBackoffStrategyConfig::timeUnit).ifPresent(u -> this.timeUnit = u);
        LOGGER.debug("Using polynomial strategy with delay {}, maximum delay {}, retries {} ant time unit {}",
                this.delay, this.maxDelay, this.maxRetries, this.timeUnit);
    }

    /**
     * Return a polynomial back-off delay:
     *
     * <code>
     * f(x, y) = x * (y - 1)^2
     * </code>
     *
     * where x is the delay and y is the failure count. If the failure count is 0 a delay of 0 will be returned.
     */
    @Override
    public int delay() {
        if (failures.get() > 0) {
            long backoff = Math.round(delay * Math.pow(2, (double) failures.get() - 1));
            return backoff < maxDelay ? (int) backoff : maxDelay;
        } else {
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int failures() {
        return failures.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementFailures() {
        this.failures.addAndGet(1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetFailures() {
        this.failures.set(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasRetriesBeenExceeded() {
        return failures.get() >= maxRetries;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TimeUnit timeUnit() {
        return timeUnit;
    }
}
