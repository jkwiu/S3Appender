package com.jk.logging;

import java.util.UUID;

import com.jk.logging.util.Event;
import com.jk.logging.util.LoggingEventCache;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.filter.AbstractFilter;

public class S3AppenderBuilder 
    extends org.apache.logging.log4j.core.appender.AbstractAppender.Builder 
    implements org.apache.logging.log4j.core.util.Builder<S3Appender> {

    // general properties
    @PluginBuilderAttribute
    private boolean verbose = false;

    @PluginBuilderAttribute
    private String tags;

    @PluginBuilderAttribute
    private int stagingBufferSize = 25;

    @PluginBuilderAttribute
    private int stagingBufferAge = 0;

    // S3 properties
    @PluginBuilderAttribute
    private String s3Bucket;

    @PluginBuilderAttribute
    private String s3Region;

    @PluginBuilderAttribute
    private String s3Path;

    @PluginBuilderAttribute
    private String s3AwsKey;

    @PluginBuilderAttribute
    private String s3AwsSecret;

    @PluginBuilderAttribute
    private String s3AwsSessionToken = null;

    @PluginBuilderAttribute
    private String s3ServiceEndpoint;

    @PluginBuilderAttribute
    private String s3SigningRegion;

    @PluginBuilderAttribute
    private String s3Compression;

    @PluginBuilderAttribute
    private String s3SseKeyType;


    @Override
    // 미확인 오퍼레이션과 관련된 경고를 억제
    @SuppressWarnings("uncheked")
    public S3Appender build() {
        try {
            String cacheName = UUID.randomUUID().toString().replaceAll("-", "");
            LoggingEventCache<Event> cache = new LoggingEventCache<>(
                cacheName, createCacheMonitor(), createCachePublisher());
            return installFilter(new S3Appender(
                getName(), getFilter(), getLayout(), 
                true, cache, property));
        } catch (Exception e) {
            throw new RuntimeException("Cannot build appender due to errors", e);
        }
    }


    S3Appender installFilter(S3Appender appender) {
        appender.addFilter(new AbstractFilter() {
            @Override
            public Result filter(final LogEvent event) {
                // To prevent infinite looping, we filter out events from
                // the publishing thread
                Result decision = Result.NEUTRAL;
                if (LoggingEventCache.PUBLISH_THREAD_NAME.equals(event.getThreadName())) {
                    decision = Result.DENY;
                }
                return decision;
            }});
        return appender;
    }
        
        
}