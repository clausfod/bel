package dk.nykredit.resilience;

/**
 * Thrown if an error occurs executing code using the {@link ResilientExecutor}
 */
public class ResilientExecutorException extends RuntimeException {

    private static final long serialVersionUID = -5854590399858271643L;

    public ResilientExecutorException(String message) {
        super(message);
    }

    public ResilientExecutorException(String message, Throwable cause) {
        super(message, cause);
    }

}
