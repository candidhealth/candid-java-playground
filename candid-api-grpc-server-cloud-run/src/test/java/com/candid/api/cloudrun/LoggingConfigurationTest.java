package com.candid.api.cloudrun;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests logging configuration to ensure it's properly set up for Cloud Run.
 *
 * Cloud Run requirements:
 * - Logs must be written to stdout/stderr
 * - Structured JSON format is preferred for Cloud Logging integration
 * - Severity levels should be properly mapped
 * - MDC context (trace IDs, etc.) should be included
 */
class LoggingConfigurationTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(CloudRunServer.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        logger.detachAppender(listAppender);
    }

    @Test
    void testLoggingConfigurationIsLoaded() {
        // When: Log a message
        logger.info("Test message");

        // Then: Message is captured
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getMessage()).isEqualTo("Test message");
        assertThat(event.getLevel().toString()).isEqualTo("INFO");
    }

    @Test
    void testMdcContextIsPropagated() {
        // Given: MDC context with trace ID
        MDC.put("trace_id", "test-trace-123");
        MDC.put("request_id", "test-request-456");

        // When: Log a message
        logger.info("Message with trace context");

        // Then: MDC values are included
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getMDCPropertyMap())
                .containsEntry("trace_id", "test-trace-123")
                .containsEntry("request_id", "test-request-456");
    }

    @Test
    void testDifferentLogLevels() {
        // When: Log at different levels
        logger.trace("Trace message");
        logger.debug("Debug message");
        logger.info("Info message");
        logger.warn("Warn message");
        logger.error("Error message");

        // Then: All levels are captured
        // Note: Trace and Debug may be filtered by default configuration
        assertThat(listAppender.list)
                .hasSizeGreaterThanOrEqualTo(3)
                .extracting(ILoggingEvent::getLevel)
                .extracting(Object::toString)
                .contains("INFO", "WARN", "ERROR");
    }

    @Test
    void testExceptionLogging() {
        // Given: An exception
        Exception exception = new RuntimeException("Test exception");

        // When: Log with exception
        logger.error("Error occurred", exception);

        // Then: Exception is included
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getMessage()).isEqualTo("Error occurred");
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo("java.lang.RuntimeException");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("Test exception");
    }

    @Test
    void testLoggerNameIsPreserved() {
        // When: Log a message
        logger.info("Test message");

        // Then: Logger name is preserved
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLoggerName()).isEqualTo(CloudRunServer.class.getName());
    }

    @Test
    void testThreadNameIsIncluded() {
        // When: Log a message
        logger.info("Test message");

        // Then: Thread name is included
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getThreadName()).isNotNull().isNotEmpty();
    }
}
