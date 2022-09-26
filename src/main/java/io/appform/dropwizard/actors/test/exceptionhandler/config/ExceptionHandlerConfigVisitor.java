package io.appform.dropwizard.actors.test.exceptionhandler.config;

/**
 * Created by kanika.khetawat on 04/02/20
 */
public interface ExceptionHandlerConfigVisitor<T> {

    T visit(DropConfig config);

    T visit(SidelineConfig config);
}
