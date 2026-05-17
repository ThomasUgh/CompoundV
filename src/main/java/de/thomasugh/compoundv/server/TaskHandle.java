package de.thomasugh.compoundv.server;

@FunctionalInterface
public interface TaskHandle {
    TaskHandle NOOP = () -> { };

    void cancel();
}
