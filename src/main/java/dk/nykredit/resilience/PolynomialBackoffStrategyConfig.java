package dk.nykredit.resilience;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for the polynomial back-off strategy.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface PolynomialBackoffStrategyConfig {

    int delay();
    int maxDelay();
    int retries();
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
