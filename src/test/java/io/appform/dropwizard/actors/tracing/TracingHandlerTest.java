package io.appform.dropwizard.actors.tracing;

import com.rabbitmq.client.AMQP;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.mock.MockTracer;
import io.opentracing.noop.NoopScopeManager;
import io.opentracing.noop.NoopSpan;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalScope;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

@Slf4j
class TracingHandlerTest {

    @Test
    void testGetTracer() {
        Assertions.assertNotNull(TracingHandler.getTracer());
    }

    @Test
    void testActivateSpan() {
        Assertions.assertNull(TracingHandler.activateSpan(null, NoopSpan.INSTANCE));
        Assertions.assertNull(TracingHandler.activateSpan(GlobalTracer.get(), null));
        val scope = TracingHandler.activateSpan(GlobalTracer.get(), NoopSpan.INSTANCE);
        Assertions.assertNotNull(scope);
        Assertions.assertTrue(scope instanceof NoopScopeManager.NoopScope);
        val tracer = new MockTracer();
        val span = tracer.buildSpan("testSpan").start();
        val scope2 = TracingHandler.activateSpan(tracer, span);
        Assertions.assertNotNull(scope2);
        Assertions.assertTrue(scope2 instanceof ThreadLocalScope);
    }

    @Test
    void testCloseScopeAndSpan() {
        Assertions.assertDoesNotThrow(() -> TracingHandler.closeScopeAndSpan(null, null));
        val tracer = new MockTracer();
        val span = tracer.buildSpan("testSpan").start();
        Assertions.assertNull(tracer.activeSpan());
        Scope scope = TracingHandler.activateSpan(tracer, span);
        Assertions.assertNotNull(tracer.activeSpan());
        Assertions.assertNotNull(scope);
        Assertions.assertTrue(scope instanceof ThreadLocalScope);
        Assertions.assertDoesNotThrow(() -> TracingHandler.closeScopeAndSpan(span, scope));
        Assertions.assertNull(tracer.activeSpan());
    }

    @Test
    void testBuildSpanWhenThereIsNoActiveSpanAndSpanIdAlsoNotPresentInHeaders() {
        val exchange = "exchange";
        val routingKey = "routingKeyValue";
        Assertions.assertNull(TracingHandler.buildSpan(exchange, routingKey, null, null));
        val tracer = new MockTracer();
        val headers = new HashMap<String, Object>();
        headers.put("testKey", "testValue");
        val properties = new AMQP.BasicProperties().builder().headers(headers).build();
        val span = TracingHandler.buildSpan(exchange, routingKey, properties, tracer);
        val scope = TracingHandler.activateSpan(tracer, span);
        Assertions.assertNotNull(span);
        Assertions.assertNotNull(scope);
        Assertions.assertTrue(scope instanceof ThreadLocalScope);
        TracingHandler.closeScopeAndSpan(span, scope);
        val finishedSpan = tracer.finishedSpans().get(0);
        Assertions.assertEquals("send", finishedSpan.operationName());
        Assertions.assertEquals(0, finishedSpan.references().size());
        val tags = finishedSpan.tags();
        Assertions.assertEquals(4, tags.size());
        Assertions.assertEquals("producer", tags.get("span.kind"));
        Assertions.assertEquals("java-rabbitmq", tags.get("component"));
        Assertions.assertEquals("java-rabbitmq", tags.get("component"));
        Assertions.assertEquals(exchange, tags.get("message_bus.destination"));
        Assertions.assertEquals(1, properties.getHeaders().size());
        Assertions.assertEquals("testValue", properties.getHeaders().get("testKey"));
    }

    @Test
    void testBuildSpanWhenThereIsActiveSpanAndSpanIdNotPresentInHeaders() {
        val exchange = "exchange";
        val routingKey = "routingKeyValue";
        Assertions.assertNull(TracingHandler.buildSpan(exchange, routingKey, null, null));
        val tracer = new MockTracer();
        val parentSpan = tracer.buildSpan("parentSpan").start();
        val parentScope = TracingHandler.activateSpan(tracer, parentSpan);
        Assertions.assertNotNull(parentScope);
        Assertions.assertNotNull(parentScope);
        Assertions.assertTrue(parentScope instanceof ThreadLocalScope);
        val headers = new HashMap<String, Object>();
        headers.put("testKey", "testValue");
        val properties = new AMQP.BasicProperties().builder().headers(headers).build();
        val span = TracingHandler.buildSpan(exchange, routingKey, properties, tracer);
        val scope = TracingHandler.activateSpan(tracer, span);
        Assertions.assertNotNull(span);
        Assertions.assertNotNull(scope);
        Assertions.assertTrue(scope instanceof ThreadLocalScope);
        TracingHandler.closeScopeAndSpan(span, scope);
        val finishedSpan = tracer.finishedSpans().get(0);
        Assertions.assertEquals("send", finishedSpan.operationName());
        Assertions.assertEquals(1, finishedSpan.references().size());
        Assertions.assertEquals(References.CHILD_OF, finishedSpan.references().get(0).getReferenceType());
        val tags = finishedSpan.tags();
        Assertions.assertEquals(4, tags.size());
        Assertions.assertEquals("producer", tags.get("span.kind"));
        Assertions.assertEquals("java-rabbitmq", tags.get("component"));
        Assertions.assertEquals("java-rabbitmq", tags.get("component"));
        Assertions.assertEquals(exchange, tags.get("message_bus.destination"));
        Assertions.assertEquals(1, properties.getHeaders().size());
        Assertions.assertEquals("testValue", properties.getHeaders().get("testKey"));
    }

    @Test
    void testBuildSpanWhenThereIsNoActiveSpanAndSpanIdIsPresentInHeaders() {
        val exchange = "exchange";
        val routingKey = "routingKeyValue";
        Assertions.assertNull(TracingHandler.buildSpan(exchange, routingKey, null, null));
        val tracer = new MockTracer();
        val parentSpan = tracer.buildSpan("testSpan").start();
        val headers = new HashMap<String, Object>();
        headers.put("testKey", "testValue");
        tracer.inject(parentSpan.context(), Format.Builtin.TEXT_MAP, new HeadersMapInjectAdapter(headers));
        val properties = new AMQP.BasicProperties().builder().headers(headers).build();
        val span = TracingHandler.buildSpan(exchange, routingKey, properties, tracer);
        val scope = TracingHandler.activateSpan(tracer, span);
        Assertions.assertNotNull(span);
        Assertions.assertNotNull(scope);
        Assertions.assertTrue(scope instanceof ThreadLocalScope);
        TracingHandler.closeScopeAndSpan(span, scope);
        val finishedSpan = tracer.finishedSpans().get(0);
        Assertions.assertEquals("send", finishedSpan.operationName());
        Assertions.assertEquals(1, finishedSpan.references().size());
        Assertions.assertEquals(References.CHILD_OF, finishedSpan.references().get(0).getReferenceType());
        val tags = finishedSpan.tags();
        Assertions.assertEquals(4, tags.size());
        Assertions.assertEquals("producer", tags.get("span.kind"));
        Assertions.assertEquals("java-rabbitmq", tags.get("component"));
        Assertions.assertEquals("java-rabbitmq", tags.get("component"));
        Assertions.assertEquals(exchange, tags.get("message_bus.destination"));
        Assertions.assertEquals(3, properties.getHeaders().size());
        Assertions.assertEquals("testValue", properties.getHeaders().get("testKey"));
        Assertions.assertNotNull(properties.getHeaders().get("spanid"));
        Assertions.assertNotNull(properties.getHeaders().get("traceid"));
    }

    @Test
    void testInjectMethod() {
        val headers = new HashMap<String, Object>();
        headers.put("testKey", "testValue");
        val props = new AMQP.BasicProperties().builder().headers(headers).build();
        val tracer = new MockTracer();
        val span = tracer.buildSpan("testSpan").start();
        val properties0 = TracingHandler.inject(props,null,GlobalTracer.get());
        Assertions.assertNotNull(properties0);
        Assertions.assertEquals("testValue", properties0.getHeaders().get("testKey"));
        Assertions.assertNotNull(TracingHandler.inject(props,span,null));
        val properties1 = TracingHandler.inject(null, span,tracer);
        Assertions.assertNotNull(properties1);
        Assertions.assertNotNull(properties1.getHeaders());
        Assertions.assertEquals(2,properties1.getHeaders().size());
        Assertions.assertNotNull(properties1.getHeaders().get("spanid"));
        Assertions.assertNotNull(properties1.getHeaders().get("traceid"));
        val properties2 = TracingHandler.inject(props,span,tracer);
        Assertions.assertNotNull(properties2);
        Assertions.assertNotNull(properties2.getHeaders());
        Assertions.assertEquals(3,properties2.getHeaders().size());
        Assertions.assertNotNull(properties2.getHeaders().get("testKey"));
        Assertions.assertNotNull(properties2.getHeaders().get("spanid"));
        Assertions.assertNotNull(properties2.getHeaders().get("traceid"));
    }

    @Test
    void testBuildChildSpan() {
        Assertions.assertNull(TracingHandler.buildChildSpan(null, GlobalTracer.get()));
        val tracer = new MockTracer();
        val parentSpan = tracer.buildSpan("testSpan").start();
        val headers = new HashMap<String, Object>();
        headers.put("testKey", "testValue");
        tracer.inject(parentSpan.context(), Format.Builtin.TEXT_MAP, new HeadersMapInjectAdapter(headers));
        val properties = new AMQP.BasicProperties().builder().headers(headers).build();
        val span = TracingHandler.buildChildSpan(properties, tracer);
        val scope = TracingHandler.activateSpan(tracer, span);
        Assertions.assertNotNull(span);
        Assertions.assertNotNull(scope);
        Assertions.assertTrue(scope instanceof ThreadLocalScope);
        TracingHandler.closeScopeAndSpan(span, scope);
        val finishedSpan = tracer.finishedSpans().get(0);
        Assertions.assertEquals("receive", finishedSpan.operationName());
        Assertions.assertEquals(parentSpan.context().spanId(), finishedSpan.parentId());
        Assertions.assertEquals(1, finishedSpan.references().size());
        Assertions.assertEquals(References.FOLLOWS_FROM, finishedSpan.references().get(0).getReferenceType());
        val tags = finishedSpan.tags();
        Assertions.assertEquals(2, tags.size());
        Assertions.assertEquals("consumer", tags.get("span.kind"));
        Assertions.assertEquals("java-rabbitmq", tags.get("component"));
        Assertions.assertEquals(3, properties.getHeaders().size());
        Assertions.assertEquals("testValue", properties.getHeaders().get("testKey"));
        Assertions.assertNotNull(properties.getHeaders().get("spanid"));
        Assertions.assertNotNull(properties.getHeaders().get("traceid"));
    }

    @Test
    void testBuildChildSpanWhenTracerIsNotNoopTracer() {
        val tracer = new MockTracer();
        val parentSpan = tracer.buildSpan("testSpan").start();
        val headers = new HashMap<String, Object>();
        tracer.inject(parentSpan.context(), Format.Builtin.TEXT_MAP, new HeadersMapInjectAdapter(headers));
        val properties = new AMQP.BasicProperties().builder().headers(headers).build();
        Assertions.assertNotNull(TracingHandler.buildChildSpan(properties, tracer));
    }

    @Test
    void comparisonCheck() {
        val x = "MyNameIsShobhit";
        val time1 = ObjectsNull(x);
        val time2 = InlineNull(x);
        log.info("time1: {}",time1);
        log.info("time2: {}",time2);
    }

    private long ObjectsNull(String x) {
//        val start = Instant.now().toEpochMilli();
        val start = System.nanoTime();
        Objects.isNull(x);
//        val end = Instant.now().toEpochMilli();
        val end = System.nanoTime();
        return end-start;
    }

    private long InlineNull(String x) {
//        val start = Instant.now().toEpochMilli();
        val start = System.nanoTime();
        val y= x==null;
//        val end = Instant.now().toEpochMilli();
        val end = System.nanoTime();
        return end-start;
    }
}
