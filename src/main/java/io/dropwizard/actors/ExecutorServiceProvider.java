package io.dropwizard.actors;

import java.util.concurrent.ExecutorService;

public interface ExecutorServiceProvider {

    ExecutorService newFixedThreadPool(String name, int coreSize);

}