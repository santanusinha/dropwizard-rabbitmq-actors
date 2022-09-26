package com.phonepe.platform.exceptionhandler.handlers;

import com.phonepe.platform.exceptionhandler.config.DropConfig;

/**
 * Created by kanika.khetawat on 04/02/20
 */
public class MessageDropHandler extends ExceptionHandler {

    public MessageDropHandler(DropConfig dropConfig) {
        super(dropConfig);
    }

    @Override
    public boolean handle() {
        return true;
    }
}
