package io.appform.dropwizard.actors.actor;

public interface QueueTypeVisitor<T> {

    T visitClassicQueue();

    T visitQuorumQueue();

    T visitClassicV2Queue();
}
