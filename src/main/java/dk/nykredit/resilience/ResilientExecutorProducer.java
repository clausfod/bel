package dk.nykredit.resilience;

import java.util.Optional;

import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI Producer for creating a new {@link ResilientExecutor}, based on annotation present at injection point.
 */
@Dependent
public class ResilientExecutorProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientExecutorProducer.class);

    @Produces
    @Resilient
    public ResilientExecutor createResilientExecutor(InjectionPoint ip) {
        Optional<Resilient> conf = Optional.ofNullable(ip.getAnnotated().getAnnotation(Resilient.class));

        BackOffStrategy strategy = conf.map(a -> createStrategy(a, ip))
                .orElse(new PolynomialBackoffStrategy());

        String executor = conf.map(Resilient::executor)
                .orElse(ResilientExecutor.DEFAULT_SCHEDULED_EXECUTOR_SERVICE);

        return new ResilientExecutor(strategy, getExecutor(executor));
    }

    private static BackOffStrategy createStrategy(Resilient a, InjectionPoint ip) {
        try {
            BackOffStrategy strategy = a.strategy().newInstance();
            strategy.configure(ip);
            return strategy;
        } catch (InstantiationException | IllegalAccessException ex) {
            LOGGER.error("Error getting strategy '" + a.executor() + "'... using default polynomial backoff strategy", ex);
            return new PolynomialBackoffStrategy();
        }
    }

    ManagedScheduledExecutorService getExecutor(String managedScheduledExecutorService) {
        if (managedScheduledExecutorService == null || managedScheduledExecutorService.isEmpty()) {
            throw new ResilientExecutorException("Ressource for managed scheduled executor service is null or empty");
        }
        try {
            InitialContext ctx = new InitialContext();
            return (ManagedScheduledExecutorService) ctx.lookup("java:comp/" + managedScheduledExecutorService);
        } catch (NamingException ex) {
            throw new ResilientExecutorException("Unable to performe loockup of managed scheduled executor service: "
                    + managedScheduledExecutorService, ex);
        }
    }

}
