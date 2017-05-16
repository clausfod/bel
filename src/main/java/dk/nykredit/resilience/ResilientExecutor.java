package dk.nykredit.resilience;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.Dependent;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The resilient executor is a simple wrapper around the Java EE {@link javax.enterprise.concurrent.ManagedScheduledExecutorService}. That makes
 * it easy to call potentially fragile services without overloading them. It will retry code execution based on a back-off strategy - i.e. a
 * class implementing the {@link dk.nykredit.resilience.BackOffStrategy}.
 *
 * The resilient executor will run code in a new transaction similar to the {@link javax.ejb.Asynchronous} annotation - this is different from
 * the behavior of {@link javax.enterprise.concurrent.ManagedScheduledExecutorService} that requires a new transaction to be stated manually.
 *
 * Using the resilient executor is similar to using Java EE {@link javax.enterprise.concurrent.ManagedExecutorService} - use it as follows:
 *
 * <pre>
 * {@code
 *  {@literal @}Inject
 *  {@literal @}ResilientExecutorConfig(strategy = PolynomialBackoffStrategy.class)
 *  {@literal @}PolynomialBackoffStrategyConfig(delay = 1, maxDelay = 1800, retries = 100, timeUnit = TimeUnit.SECONDS)
 *  private ResilientExecutor executor;
 *
 *  executor.execute(() -> {
 *          Customer customer = archivist.get(id);
 *          RkiStatusRepresentation rki = rkiConnector.get(customer.getId());
 *          customer.setRkiStatus(rki.getStatus());
 *      }, e -> {
 *          LOGGER.error("Error calling service...", e);
 *          Customer customer = archivist.get(id);
 *          customer.setRkiStatus(RkiStatus.UNKNOWN);
 *      });
 * }
 * </pre>
 *
 * Note, the resilient executor is a dependent CDI bean and will thus be instantiated in the same scope as the bean it is injected into, to only
 * have a single instance of a resilient executor use {@link javax.ejb.Singleton} or {@link javax.enterprise.context.ApplicationScoped}.
 *
 */
@Dependent
public class ResilientExecutor implements Executor {

    public static final String DEFAULT_SCHEDULED_EXECUTOR_SERVICE = "DefaultManagedScheduledExecutorService";

    private static final String USER_TRANSACTION = "java:comp/UserTransaction";
    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientExecutor.class);

    private static final Consumer<Exception> DEFAULT_ON_FAILURE = e -> LOGGER.warn("Failed to execute code using scheduled executor service", e);

    private final BackOffStrategy strategy;
    private final ManagedScheduledExecutorService executor;

    ResilientExecutor(BackOffStrategy strategy, ManagedScheduledExecutorService executor) {
        this.strategy = strategy;
        this.executor = executor;
    }

    /**
     * Execute some code using back-off strategy. Will log generic error if maximum failures are exceeded.
     *
     * @param runnable the code to execute
     */
    public void execute(Runnable runnable) {
        execute(runnable, DEFAULT_ON_FAILURE);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return submit(task, DEFAULT_ON_FAILURE);
    }

    /**
     * Execute some code using back-off strategy.
     *
     * @param runnable the code to execute
     * @param onFailure the code to execute if maximum failures are exceeded
     */
    public void execute(Runnable runnable, Consumer<Exception> onFailure) {
        submit(() -> {
            runnable.run();
            return null;
        }, onFailure);
    }

    /**
     * Execute some code using back-off strategy.
     *
     * @param <T> the type of the result
     * @param runnable the code to execute
     * @param onFailure the code to execute if maximum failures are exceeded
     * @return a future that will block until some result is returned or maximum retries has be exceeded. In the last case the exception thrown
     * by the task will be thrown when obtaining the result from the future
     */
    public <T> Future<T> submit(Callable<T> task, Consumer<Exception> onFailure) {
        CompletableFuture<T> futureResult = new CompletableFuture<>();
        executor.schedule(() -> {
            try {
                executeTask(futureResult, task);
            } catch (Exception e) {
                executeOnFailure(futureResult, e, task, onFailure);
            }
        }, strategy.delay(), strategy.timeUnit());
        return futureResult;
    }

    private <T> void executeTask(CompletableFuture<T> future, Callable<T> task) throws Exception {
        T t = executeInTransaction(task);
        future.complete(t);
        strategy.resetFailures();
    }

    private <T> void executeOnFailure(CompletableFuture<T> future, Exception e, Callable<T> task, Consumer<Exception> onFailure) {
        strategy.incrementFailures();
        if (strategy.hasRetriesBeenExceeded()) {
            LOGGER.info("Maximum retries {} for strategy {} has be exceeded... will not try to execute code again",
                    strategy.getClass().getName(), strategy.getMaxRetries());
            executeOnFailureInTransaction(e, onFailure);
            future.completeExceptionally(e);
        } else {
            LOGGER.info("Could not execute... will retry. Failure count is {}. Delay is {} {}",
                    strategy.failures(),
                    strategy.delay(),
                    strategy.timeUnit().toString().toLowerCase(),
                    e);
            submit(task, onFailure);
        }
    }

    protected BackOffStrategy getStrategy() {
        return strategy;
    }

    /**
     * Injecting <code>UserTransaction</code> using resource annotation doesn't seem to work
     */
    protected UserTransaction getUserTransaction() {
        try {
            InitialContext ctx = new InitialContext();
            return (UserTransaction) ctx.lookup(USER_TRANSACTION);
        } catch (NamingException ex) {
            throw new ResilientExecutorException("Error getting user transaction... could not lookup "
                    + USER_TRANSACTION
                    + " from context", ex);
        }
    }

    /**
     * We need manually to begin the transaction when running using the Java EE executor service
     */
    private <T> T executeInTransaction(Callable<T> task) throws Exception {
        T result = null;
        UserTransaction ut = null;
        try {
            ut = getUserTransaction();
            ut.begin();
            result = task.call();
            ut.commit();
        } catch (Exception e) {
            rollback(ut);
            throw e;
        }
        return result;
    }

    private void executeOnFailureInTransaction(Exception failure, Consumer<Exception> onFailure) {
        UserTransaction ut = null;
        try {
            ut = getUserTransaction();
            ut.begin();
            onFailure.accept(failure);
            ut.commit();
        } catch (Exception e) {
            rollback(ut);
            LOGGER.warn("Error executing failure handeling code", e);
        }
    }

    private void rollback(UserTransaction ut) {
        try {
            if (ut != null) {
                ut.rollback();
            }
        } catch (IllegalStateException | SecurityException | SystemException ex) {
            LOGGER.error("Error performing roll-back...", ex);
        }
    }
}
