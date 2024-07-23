package io.appform.dropwizard.actors.failurehandler.config;

public interface FailureHandlerConfigVisitor<R> {

    R visit(DropConfig config);

    R visit(SidelineConfig config);
}
