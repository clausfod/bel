package dk.nykredit.resilience;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface ResilientExecutorConfig {

    Class<? extends BackOffStrategy> strategy();

    String executor() default ResilientExecutor.DEFAULT_SCHEDULED_EXECUTOR_SERVICE;

}
