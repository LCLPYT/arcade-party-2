package work.lclpnet.ap2.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.Prototype;

import java.util.Objects;

@Prototype
public final class Result<T> {

    @Nullable
    private final T result;
    @Nullable
    private final Throwable error;

    private Result(@Nullable T result, @Nullable Throwable error) {
        this.result = result;
        this.error = error;
    }

    public boolean isEmpty() {
        return result == null;
    }

    @NotNull
    public T get() {
        if (isEmpty()) {
            throw new IllegalStateException("Invalid use of getResult(): check if the result is empty first!");
        }

        return result;
    }

    @NotNull
    public Throwable getError() {
        if (error == null) {
            throw new IllegalStateException("Invalid use of getError(): check if the result is empty first!");
        }

        return error;
    }

    public void throwError() throws RuntimeException {
        if (error == null) {
            throw new NullPointerException("Error cannot be thrown because it is null");
        }

        throw new EmptyResultException(error);
    }

    public void logError(Logger logger) {
        if (error == null) {
            logger.warn("Invalid use of logError(): There is no error, check if the result is empty first");
            return;
        }

        logger.error("Unexpected result", error);
    }

    public static <T> Result<T> success(@NotNull T result) {
        Objects.requireNonNull(result, "A successful result cannot be null");
        return new Result<>(result, null);
    }

    public static <T> Result<T> failure(@NotNull Throwable error) {
        Objects.requireNonNull(error, "A failed result must have an error");
        return new Result<>(null, error);
    }
}
