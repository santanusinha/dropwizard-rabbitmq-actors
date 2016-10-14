package io.dropwizard.actors.connectivity;

/**
 * Created by santanu on 22/3/16.
 */
public enum  MessageType {
    REVOLVER_CALLBACK,
    MISSED_PAYMENT_ATTEMPT,
    COLLECTION_REQUEST_STATE_CHANGE,
    FAILED_PAYMENT_WALLET_REVERSAL;
}
