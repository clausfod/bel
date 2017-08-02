package dk.nykredit.resilience;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface Resilient {

    Class<? extends BackOffStrategy> strategy() default PolynomialBackoffStrategy.class;

    String executor() default ResilientExecutor.DEFAULT_SCHEDULED_EXECUTOR_SERVICE;

}
