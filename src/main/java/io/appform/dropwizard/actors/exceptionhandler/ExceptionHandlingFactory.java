package io.appform.dropwizard.actors.exceptionhandler;

import io.appform.dropwizard.actors.exceptionhandler.config.DropConfig;
import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfigVisitor;
import io.appform.dropwizard.actors.exceptionhandler.config.SidelineConfig;
import io.appform.dropwizard.actors.exceptionhandler.handlers.MessageDropHandler;
import io.appform.dropwizard.actors.exceptionhandler.handlers.MessageSidelineHandler;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;

/**
 * Created by kanika.khetawat on 04/02/20
 */
public class ExceptionHandlingFactory {

    public ExceptionHandler create(ExceptionHandlerConfig config) {
        if(config == null) {
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
