package work.lclpnet.ap2.api.base.prot;

public interface ProtectionType<T> {

    /**
     * Gets the scope that encompasses everything.
     * @return The global scope.
     */
    T getGlobalScope();

    /**
     * Get the resulting scope that is the difference between the disallowed and the allowed scope.
     * @param disallowed The disallowed scope.
     * @param allowed The allowed scope.
     * @return The resulting (difference) scope.
     */
    T getResultingScope(T disallowed, T allowed);
}
