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
        val spanBuilder = tracer.buildSpan("send")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER)
                .withTag("routingKey", routingKey);

        SpanContext spanContext = null;

        try {
            if (!Objects.isNull(props) && !Objects.isNull(props.getHeaders())) {
                spanContext = tracer.extract(Format.Builtin.TEXT_MAP,
                        new HeadersMapExtractAdapter(props.getHeaders()));
            }
        } catch (Exception e) {
            //this exception will arise incase someone tries to mutate the unmodifiable map -> props.getHeaders()
            log.error("cannot modify an unmodifiable map", e);
        }


        if (spanContext == null) {
            Span parentSpan = tracer.activeSpan();
            if (parentSpan != null) {
                spanContext = parentSpan.context();
            }
        }

        if (spanContext != null) {
            spanBuilder.asChildOf(spanContext);
        }

        val span = spanBuilder.start();
        SpanDecorator.onRequest(exchange, span);

        return span;
    }

    public static Scope activateSpan(final Tracer tracer,
                                     final Span span) {
        try {
            if (tracer == null || span == null) {
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
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.finish();
            }
        } catch (Exception e) {
            log.error("Error while closing span and scope", e);
        }
    }

    public static AMQP.BasicProperties inject(AMQP.BasicProperties properties, Span span,
                                              Tracer tracer) {

        // Headers of AMQP.BasicProperties is unmodifiableMap therefore we build new AMQP.BasicProperties
        // with injected span context into headers
        val headers = new HashMap<String, Object>();

        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new HeadersMapInjectAdapter(headers));

        if (properties == null) {
            return new AMQP.BasicProperties().builder().headers(headers).build();
        }

        if (properties.getHeaders() != null) {
            headers.putAll(properties.getHeaders());
        }

        return properties.builder()
                .headers(headers)
                .build();
    }

    public static Span buildChildSpan(AMQP.BasicProperties props, Tracer tracer) {
        val spanBuilder = tracer.buildSpan("receive")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER);


        val parentContext = extract(props, tracer);
        if (parentContext != null) {
            spanBuilder.addReference(References.FOLLOWS_FROM, parentContext);
        }

        val span = spanBuilder.start();
        SpanDecorator.onResponse(span);

        try {
            if (!Objects.isNull(props.getHeaders())) {
                tracer.inject(span.context(), Format.Builtin.TEXT_MAP,
                        new HeadersMapInjectAdapter(props.getHeaders()));
            }
        } catch (Exception e) {
            //this exception will arise incase someone tries to mutate the unmodifiable map -> props.getHeaders()
            log.error("cannot modify an unmodifiable map", e);
        }

        return span;
    }

    private static SpanContext extract(AMQP.BasicProperties props, Tracer tracer) {
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
