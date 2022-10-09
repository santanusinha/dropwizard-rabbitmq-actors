package io.appform.dropwizard.actors.tracing;

import com.rabbitmq.client.AMQP;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;
import io.opentracing.noop.NoopScopeManager;
import io.opentracing.noop.NoopSpan;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import lombok.val;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;

public class TracingHandlerTest {
    private Tracer tracer;

    @BeforeEach
    void setup() {
        this.tracer = Mockito.mock(Tracer.class);
    }

    @Test
    void testGetTracer() {
        Assertions.assertNotNull(TracingHandler.getTracer());
    }

//    @Test
//    public void testGetParentActiveSpan() {
//        Assert.assertNull(TracingHandler.getParentActiveSpan(null));
//        Span span = TracingHandler.getParentActiveSpan(GlobalTracer.get());
//        Assert.assertNotNull(span);
//        Assert.assertTrue(span instanceof NoopSpan);
//    }
//
//    @org.junit.Test
//    public void testStartChildSpan() {
//        Assert.assertNull(TracingHandler.startChildSpan(null, NoopSpan.INSTANCE, "test"));
//        Assert.assertNull(TracingHandler.startChildSpan(GlobalTracer.get(), null, "test"));
//        Span span = TracingHandler.startChildSpan(GlobalTracer.get(), NoopSpan.INSTANCE, "test");
//        Assert.assertNotNull(span);
//        Assert.assertTrue(span instanceof NoopSpan);
//    }

    @Test
    void testActivateSpan() {
        Assert.assertNull(TracingHandler.activateSpan(null, NoopSpan.INSTANCE));
        Assert.assertNull(TracingHandler.activateSpan(GlobalTracer.get(), null));
        Scope scope = TracingHandler.activateSpan(GlobalTracer.get(), NoopSpan.INSTANCE);
        Assert.assertNotNull(scope);
        Assert.assertTrue(scope instanceof NoopScopeManager.NoopScope);
    }

    @Test
    void testCloseScopeAndSpan() {
        Assertions.assertDoesNotThrow(() -> TracingHandler.closeScopeAndSpan(null,null));
        Assertions.assertDoesNotThrow(() -> TracingHandler.closeScopeAndSpan(NoopSpan.INSTANCE,NoopScopeManager.NoopScope.INSTANCE));
    }

    @Test
    void testBuildChildSpan() {
        Assertions.assertNull(TracingHandler.buildChildSpan(null, GlobalTracer.get()));
        val parentSpan = GlobalTracer.get().buildSpan("testSpan").start();
        val headers = new HashMap<String, Object>();
        tracer.inject(parentSpan.context(), Format.Builtin.TEXT_MAP, new HeadersMapInjectAdapter(headers));
        val properties = new AMQP.BasicProperties().builder().headers(headers).build();
        Assertions.assertNotNull(TracingHandler.buildChildSpan(properties,GlobalTracer.get()));
    }

    @Test
    void testBuildChildSpanWhenTracerIsNotNoopTracer() {
        val tracer = new MockTracer();
        val parentSpan = tracer.buildSpan("testSpan").start();
        val headers = new HashMap<String, Object>();
        tracer.inject(parentSpan.context(), Format.Builtin.TEXT_MAP, new HeadersMapInjectAdapter(headers));
        val properties = new AMQP.BasicProperties().builder().headers(headers).build();
        Assertions.assertNotNull(TracingHandler.buildChildSpan(properties,tracer));
    }
}
