package io.appform.dropwizard.actors.retry;

/**
 * Decides how the wait happens during the retry's delay.
 */
public enum WaitMode {

    SYNC,

    ASYNC

}
