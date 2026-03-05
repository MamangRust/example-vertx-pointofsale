package com.sanedge.example_crud.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

public class TracingMetrics {
  private final Tracer tracer;
  private final Meter meter;
  private final LongCounter requestCounter;
  private final DoubleHistogram requestDurationHistogram;
  private final TextMapPropagator propagator;

  private static final AttributeKey<String> METHOD_KEY = AttributeKey.stringKey("method");
  private static final AttributeKey<String> STATUS_KEY = AttributeKey.stringKey("status");

  public TracingMetrics(OpenTelemetry openTelemetry, String instrumentationName) {
    this.tracer = openTelemetry.getTracer(instrumentationName);
    this.meter = openTelemetry.getMeter(instrumentationName);
    this.propagator = openTelemetry.getPropagators().getTextMapPropagator();

    this.requestCounter = meter.counterBuilder("requests_total")
        .setDescription("Total number of requests")
        .build();

    this.requestDurationHistogram = meter.histogramBuilder("request_duration_seconds")
        .setDescription("Request duration in seconds")
        .setUnit("s")
        .build();
  }

  private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
      return carrier.keySet();
    }

    @Override
    public String get(Map<String, String> carrier, String key) {
      return carrier.get(key);
    }
  };

  public void injectContext(Context context, Map<String, String> carrier) {
    propagator.inject(context, carrier, Map::put);
  }

  public Context extractContext(Map<String, String> carrier) {
    return propagator.extract(Context.current(), carrier, MAP_GETTER);
  }

  public TracingContext startSpan(String operationName) {
    return startSpan(operationName, Attributes.empty());
  }

  public TracingContext startSpan(String operationName, Attributes attributes) {
    Instant startTime = Instant.now();

    Span span = tracer.spanBuilder(operationName)
        .setSpanKind(SpanKind.SERVER)
        .setAllAttributes(attributes)
        .startSpan();

    span.addEvent("Operation started", Attributes.builder()
        .put("operation", operationName)
        .put("timestamp", startTime.toString())
        .build());

    Context context = Context.current().with(span);

    return new TracingContext(context, startTime);
  }

  public <T> T traceAndMeasure(String operationName, String method, Supplier<T> supplier) {
    return traceAndMeasure(operationName, method, Attributes.empty(), supplier);
  }

  public <T> T traceAndMeasure(String operationName, String method, Attributes attributes, Supplier<T> supplier) {
    TracingContext tracingContext = startSpan(operationName, attributes);

    try (Scope scope = tracingContext.getContext().makeCurrent()) {
      T result = supplier.get();
      completeSpanSuccess(tracingContext, method, "Operation completed successfully");
      return result;
    } catch (Exception e) {
      completeSpanError(tracingContext, method, "Operation failed: " + e.getMessage());
      throw e;
    }
  }

  public void completeSpanSuccess(TracingContext tracingContext, String method, String message) {
    completeSpan(tracingContext, method, true, message);
  }

  public void completeSpanError(TracingContext tracingContext, String method, String errorMessage) {
    completeSpan(tracingContext, method, false, errorMessage);
  }

  private void completeSpan(TracingContext tracingContext, String method, boolean isSuccess, String message) {
    String status = isSuccess ? "SUCCESS" : "ERROR";
    double duration = Duration.between(tracingContext.getStartTime(), Instant.now()).toMillis() / 1000.0;

    Span span = Span.fromContext(tracingContext.getContext());

    span.addEvent("Operation completed", Attributes.builder()
        .put("status", status)
        .put("duration_secs", duration)
        .put("message", message)
        .build());

    if (isSuccess) {
      span.setStatus(StatusCode.OK);
    } else {
      span.setStatus(StatusCode.ERROR, message);
    }

    requestCounter.add(1, Attributes.builder()
        .put(METHOD_KEY, method)
        .put(STATUS_KEY, status)
        .build());

    requestDurationHistogram.record(duration, Attributes.builder()
        .put(METHOD_KEY, method)
        .put(STATUS_KEY, status)
        .build());

    span.end();
  }

  public static class TracingContext {
    private final Context context;
    private final Instant startTime;

    public TracingContext(Context context, Instant startTime) {
      this.context = context;
      this.startTime = startTime;
    }

    public Context getContext() {
      return context;
    }

    public Instant getStartTime() {
      return startTime;
    }
  }
}
