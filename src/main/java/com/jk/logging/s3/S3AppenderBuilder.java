package com.jk.logging.s3;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jk.logging.util.BufferPublisher;
import com.jk.logging.util.CapacityBasedBufferMonitor;
import com.jk.logging.util.Event;
import com.jk.logging.util.IBufferMonitor;
import com.jk.logging.util.IBufferPublisher;
import com.jk.logging.util.LoggingEventCache;
import com.jk.logging.util.TimePeriodBasedBufferMonitor;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import software.amazon.awssdk.services.s3.S3Client;

import static com.jk.logging.s3.AwsClientHelpers.buildClient;


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
                true, cache));
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

    Optional<S3Client> initS3ClientIfEnabled() {
        Optional<com.jk.logging.s3.S3Configuration> s3 = Optional.empty();
        if ((null != s3Bucket) && (null != s3Path)) {
            com.jk.logging.s3.S3Configuration config = new com.jk.logging.s3.S3Configuration();
            config.setBucket(s3Bucket);
            config.setPath(s3Path);
            config.setRegion(s3Region);
            config.setAccessKey(s3AwsKey);
            config.setSecretKey(s3AwsSecret);
            config.setSessionToken(s3AwsSessionToken);
            config.setServiceEndpoint(s3ServiceEndpoint);
            config.setSigningRegion(s3SigningRegion);
            s3 = Optional.of(config);
        }
        return s3.map(config -> buildClient(
            config.getAccessKey(), config.getSecretKey(), config.getSessionToken(),
            config.getRegion(),
            config.getServiceEndpoint(), config.getSigningRegion()
        ));
    }

    IBufferPublisher<Event> createCachePublisher() throws UnknownHostException {

        java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
        String hostName = addr.getHostName();
        BufferPublisher<Event> publisher = new BufferPublisher<Event>(hostName, parseTags(tags));

        initS3ClientIfEnabled().ifPresent(client -> {
            if (verbose) {
                System.out.println(String.format(
                    "Registering S3 publish helper -> %s:%s", s3Bucket, s3Path));
            }
            com.jk.logging.s3.S3Configuration.S3SSEConfiguration sseConfig = null;
            if (s3SseKeyType != null) {
                sseConfig = new com.jk.logging.s3.S3Configuration.S3SSEConfiguration(
                    com.jk.logging.s3.S3Configuration.SSEType.valueOf(s3SseKeyType),
                    null
                );
            }
            publisher.addHelper(new S3PublishHelper((S3Client)client,
                s3Bucket, s3Path,
                Boolean.parseBoolean(s3Compression),
                sseConfig
            ));
        });

        return publisher;
    }

    String[] parseTags(String tags) {
        Set<String> parsedTags = null;
        if (null != tags) {
            parsedTags = Stream.of(tags.split("[,;]"))
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());
        } else {
            parsedTags = Collections.emptySet();
        }
        return parsedTags.toArray(new String[] {});
    }

    IBufferMonitor<Event> createCacheMonitor() {
        IBufferMonitor<Event> monitor = new CapacityBasedBufferMonitor<Event>(stagingBufferSize);
        if (0 < stagingBufferAge) {
            monitor = new TimePeriodBasedBufferMonitor<Event>(stagingBufferAge);
        }
        if (verbose) {
            System.out.println(String.format("Using cache monitor: %s", monitor.toString()));
        }
        return monitor;
    }
        
        
}