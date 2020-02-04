package io.appform.dropwizard.actors.postretry.config;

/**
 * Created by kanika.khetawat on 04/02/20
 */
public interface PostRetryConfigVisitor<T> {

    T visitDrop();

    T visitSideline();
}
