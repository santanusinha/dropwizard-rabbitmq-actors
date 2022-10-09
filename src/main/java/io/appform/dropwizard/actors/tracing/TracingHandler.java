package io.appform.dropwizard.actors.tracing;

import com.rabbitmq.client.AMQP;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.HashMap;
import java.util.Objects;

@UtilityClass
@Slf4j
public class TracingHandler {

    public static Tracer getTracer() {
        try {
            return GlobalTracer.get();
        } catch (Exception e) {
            log.error("Error while getting tracer", e);
            return null;
        }
    }

    public static Span buildSpan(final String exchange,
                                 final String routingKey,
                                 final AMQP.BasicProperties props,
                                 final Tracer tracer) {
        if (Objects.isNull(tracer)) {
            return null;
        }
        val spanBuilder = tracer.buildSpan("send")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER)
                .withTag("routingKey", routingKey);

        var spanContext = extract(props, tracer);

        if (Objects.isNull(spanContext)) {
            val parentSpan = tracer.activeSpan();
            if (!Objects.isNull(parentSpan)) {
                spanContext = parentSpan.context();
            }
        }

        if (!Objects.isNull(spanContext)) {
            spanBuilder.asChildOf(spanContext);
        }

        val span = spanBuilder.start();
        SpanDecorator.onRequest(exchange, span);

        populateHeadersIfAny(props, tracer, span);
        return span;
    }

    public static Scope activateSpan(final Tracer tracer,
                                     final Span span) {
        try {
            if (Objects.isNull(tracer) || Objects.isNull(span)) {
                return null;
            }
            return tracer.activateSpan(span);
        } catch (Exception e) {
            log.error("Error while activating span", e);
            return null;
        }
    }

    public static void closeScopeAndSpan(final Span span,
                                         final Scope scope) {
        try {
            if (!Objects.isNull(scope)) {
                scope.close();
            }
            if (!Objects.isNull(span)) {
                span.finish();
            }
        } catch (Exception e) {
            log.error("Error while closing span and scope", e);
        }
    }

    public static AMQP.BasicProperties inject(AMQP.BasicProperties properties, Span span,
                                              Tracer tracer) {

        if(Objects.isNull(span) || Objects.isNull(tracer)) {
            return null;
        }
        // Headers of AMQP.BasicProperties is unmodifiableMap therefore we build new AMQP.BasicProperties
        // with injected span context into headers
        val headers = new HashMap<String, Object>();

        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new HeadersMapInjectAdapter(headers));

        if (Objects.isNull(properties)) {
            return new AMQP.BasicProperties().builder().headers(headers).build();
        }

        if (!Objects.isNull(properties.getHeaders())) {
            headers.putAll(properties.getHeaders());
        }

        return properties.builder()
                .headers(headers)
                .build();
    }

    public static Span buildChildSpan(AMQP.BasicProperties props, Tracer tracer) {
        val parentContext = extract(props, tracer);
        if (Objects.isNull(parentContext)) {
            return null;
        }
        val spanBuilder = tracer.buildSpan("receive")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER);


        spanBuilder.addReference(References.FOLLOWS_FROM, parentContext);


        val span = spanBuilder.start();
        SpanDecorator.onResponse(span);

        populateHeadersIfAny(props, tracer, span);

        return span;
    }

    private static void populateHeadersIfAny(AMQP.BasicProperties props, Tracer tracer, Span span) {
        try {
            if (!Objects.isNull(props) && !Objects.isNull(props.getHeaders())) {
                val headers = new HashMap<String, Object>();
                props.getHeaders().forEach(headers::put);
                tracer.inject(span.context(), Format.Builtin.TEXT_MAP,
                        new HeadersMapInjectAdapter(headers));
                props.builder().headers(headers).build();
            }
        } catch (Exception e) {
            //this exception will arise incase someone tries to mutate the unmodifiable map -> props.getHeaders()
            log.error("something went wrong while modifying AMQP.BasicProperties.headers", e);
        }
    }

    private static SpanContext extract(AMQP.BasicProperties props, Tracer tracer) {

        if (Objects.isNull(props) || Objects.isNull(tracer)) {
            return null;
        }
        val spanContext = tracer.extract(Format.Builtin.TEXT_MAP,
                new HeadersMapExtractAdapter(props.getHeaders()));
        if (spanContext != null) {
            return spanContext;
        }

        val span = tracer.activeSpan();
        if (span != null) {
            return span.context();
        }
        return null;
    }
}
