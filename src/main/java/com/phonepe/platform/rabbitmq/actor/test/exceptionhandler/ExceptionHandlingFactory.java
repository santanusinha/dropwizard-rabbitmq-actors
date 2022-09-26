package com.phonepe.platform.rabbitmq.actor.test.exceptionhandler;

import com.phonepe.platform.rabbitmq.actor.test.exceptionhandler.config.DropConfig;
import com.phonepe.platform.rabbitmq.actor.test.exceptionhandler.config.ExceptionHandlerConfig;
import com.phonepe.platform.rabbitmq.actor.test.exceptionhandler.config.ExceptionHandlerConfigVisitor;
import com.phonepe.platform.rabbitmq.actor.test.exceptionhandler.config.SidelineConfig;
import com.phonepe.platform.rabbitmq.actor.test.exceptionhandler.handlers.ExceptionHandler;
import com.phonepe.platform.rabbitmq.actor.test.exceptionhandler.handlers.MessageDropHandler;
import com.phonepe.platform.rabbitmq.actor.test.exceptionhandler.handlers.MessageSidelineHandler;

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
