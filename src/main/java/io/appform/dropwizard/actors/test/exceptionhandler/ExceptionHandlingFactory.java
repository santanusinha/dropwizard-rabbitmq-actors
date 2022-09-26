package io.appform.dropwizard.actors.test.exceptionhandler;

import io.appform.dropwizard.actors.test.exceptionhandler.config.DropConfig;
import io.appform.dropwizard.actors.test.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.dropwizard.actors.test.exceptionhandler.config.ExceptionHandlerConfigVisitor;
import io.appform.dropwizard.actors.test.exceptionhandler.config.SidelineConfig;
import io.appform.dropwizard.actors.test.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.test.exceptionhandler.handlers.MessageDropHandler;
import io.appform.dropwizard.actors.test.exceptionhandler.handlers.MessageSidelineHandler;

/**
 * Created by kanika.khetawat on 04/02/20
 */
public class ExceptionHandlingFactory {

    public ExceptionHandler create(ExceptionHandlerConfig config) {
        if (config == null) {
            return new MessageSidelineHandler(new SidelineConfig());
        }
        return config.accept(new ExceptionHandlerConfigVisitor<ExceptionHandler>() {
            @Override
            public ExceptionHandler visit(DropConfig config) {
                return new MessageDropHandler(config);
            }

            @Override
            public ExceptionHandler visit(SidelineConfig config) {
                return new MessageSidelineHandler(config);
            }
        });
    }
}
