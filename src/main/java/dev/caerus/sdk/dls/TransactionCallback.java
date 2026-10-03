package dev.caerus.sdk.dls;

@FunctionalInterface
public interface TransactionCallback<T, E extends Exception> {

    T run(TransactionContext tx) throws E;
}
