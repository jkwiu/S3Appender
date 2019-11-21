package com.jk.logging;

import java.io.Serializable;
import java.util.Objects;

import com.jk.logging.util.Event;
import com.jk.logging.util.LoggingEventCache;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;


@Plugin(
    name = "S3Appender",
    category = "Core",
    elementType = "appender"
)
public class S3Appender extends AbstractAppender {

    private LoggingEventCache<Event> eventCache = null;
    private boolean verbose = false;

    @PluginBuilderFactory
    public static org.apache.logging.log4j.core.util.Builder<S3Appender> newBuilder() {
        return new S3AppenderBuilder();
    }

    protected S3Appender(String name, 
                        Filter filter, 
                        Layout<? extends Serializable> layout, 
                        boolean ignoreExceptions, 
                        LoggingEventCache<Event> eventCache,
                        Property[] property) {
        super(name, filter, layout, ignoreExceptions, property);
        // null이 아닐 경우 그대로 반환해주고, null일 경우 NPE 발생
        Objects.requireNonNull(eventCache);
        this.eventCache = eventCache;
        // 프로그램이 종료되어야 할 때 반드시 실행되어야 하는 코드
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run(){
                LOGGER.debug("Publishing staging log on shutdown...");
                eventCache.flushAndPublish();
            }
        });
    }

    @Override
    public void append(LogEvent logEvent) {
        try {
            eventCache.add(mapToEvent(logEvent));
        } catch (Exception e) {
            // 리턴 값이 없다. 내부적으로 예외 결과를 찍는다.
            e.printStackTrace();
        }
        // log4j2.xml에서의 format: pattern 을 말하는 것 같다.
        LOGGER.debug(String.format("S3Appender says: %s", logEvent.getMessage().getFormattedMessage()));
    }

    Event mapToEvent(LogEvent event) {
        String message = null;
        if (null != getLayout()) {
            // Layout이 있으면 이벤트를 string으로
            message = getLayout().toSerializable(event).toString();
        } else {
            // Layout이 없으면 이벤트를 받아서 string으로
            message = event.getMessage().toString();
        }
        Event mapped = new Event(
            event.getLoggerName(), 
            event.getLevel().toString(), 
            message
        );
        return mapped;
    }

}