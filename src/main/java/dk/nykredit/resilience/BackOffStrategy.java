package dk.nykredit.resilience;

import java.util.concurrent.TimeUnit;

import javax.enterprise.inject.spi.InjectionPoint;

/**
 * Back-off strategy used by {@link dk.nykredit.resilience.ResilientExecutor} to avoid overloading when calling e.g. a service.
 */
public interface BackOffStrategy {

    /**
     * The delay calculated by the back-off strategy defining how long the {@link dk.nykredit.resilience.ResilientExecutor} should wait to make 
     * the next call.
     *
     * @return the delay
     */
    int delay();

    /**
     * The maximum number of calls that should be preformed.
     *
     * @return maximum number of calls
     */
    int getMaxRetries();

    /**
     * Increment the failure count.
     */
    void incrementFailures();

    /**
     * The current number of failures.
     *
     * @return number of failures
     */
    int failures();

    /**
     * Reset the failure count to 0. This is called by the {@link dk.nykredit.resilience.ResilientExecutor} when a successful call has been
     * executed.
     */
    void resetFailures();

    /**
     * Indicates if maximum number of retries has be exceeded.
     *
     * @return true if maximum number of retries has be exceeded, false otherwise
     */
    boolean hasRetriesBeenExceeded();

    /**
     * Time unit used to calculate delay.
     *
     * @return the time unit
     */
    TimeUnit timeUnit();

    /**
     * Configure the back-off strategy using the injection point of {@link dk.nykredit.resilience.ResilientExecutor}.
     *
     * A back-off strategy can introduce it's own annotation for configuration. When the {@link dk.nykredit.resilience.ResilientExecutor} is
     * produced it will call this method allowing the strategy to configure itself.
     *
     * @param ip the injection point of the executor
     */
    void configure(InjectionPoint ip);

}
