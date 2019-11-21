package com.jk.logging.s3;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

import com.jk.logging.util.Event;
import com.jk.logging.util.IPublishHelper;
import com.jk.logging.util.PublishContext;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Implementation to publish log events to S3.
 * <br>
 * These Log4j logger parameters configure the S3 publisher:
 * <br>
 * <em>NOTES</em>:
 * <ul>
 * <li>If the access key and secret key are provided, they will be preferred over
 * whatever default setting (e.g. ~/.aws/credentials or
 * %USERPROFILE%\.aws\credentials) is in place for the
 * runtime environment.</li>
 * <li>Tags are currently ignored by the S3 publisher.</li>
 * </ul>
 *
 * @author vly
 *
 */
public class S3PublishHelper implements IPublishHelper<Event> {

    private final S3Client client;
    private final String bucket;
    private final String path;
    private boolean compressEnabled = false;
    private final S3Configuration.S3SSEConfiguration sseConfig;

    private volatile boolean bucketExists = false;

    private File tempFile;
    private Writer outputWriter;
    

    public S3PublishHelper(S3Client client, String bucket, String path, boolean compressEnabled,
                           S3Configuration.S3SSEConfiguration sseConfig) {
        this.client = client;
        this.bucket = bucket.toLowerCase();
        if (!path.endsWith("/")) {
            this.path = path + "/";
        } else {
            this.path = path;
        }
        this.compressEnabled = compressEnabled;
        this.sseConfig = sseConfig;
    }

    public void start(PublishContext context) {
        try {
            tempFile = File.createTempFile("s3Publish", null);
            OutputStream os = createCompressedStreamAsNecessary(
                new BufferedOutputStream(new FileOutputStream(tempFile)),
                compressEnabled);
            outputWriter = new OutputStreamWriter(os);
             System.out.println(
                 String.format("----------------------- Collecting content into %s before sending to S3. -----------------------", tempFile));
            // 항상 버켓은 존재한다. 권한이 없으니깐
            bucketExists = true;
               
        } catch (Exception ex) {
            throw new RuntimeException(String.format("----------------------- Cannot start publishing: %s -----------------------", ex.getMessage()), ex);
        }
    }

    public void publish(PublishContext context, int sequence, Event event) {
        try {
            outputWriter.write(event.getMessage());
        } catch (Exception ex) {
            throw new RuntimeException(
                String.format("----------------------- Cannot collect event %s: %s -----------------------", event, ex.getMessage()), ex);
        }
    }


    public void end(PublishContext context) {
        String key = String.format("%s%s", path, context.getCacheName());
		System.out.println(String.format("----------------------- Publishing to S3 (bucket=%s; key=%s): -----------------------",
			bucket, key));

        try {
            if (null != outputWriter) {
                outputWriter.close();
                outputWriter = null;

                PutObjectRequest por = PutObjectRequest.builder().bucket(bucket).key(key).build();
                
                RequestBody rqb = RequestBody.fromFile(tempFile);
                
                PutObjectResponse result = client.putObject(por, rqb);
                System.out.println(String.format("----------------------- Content MD5: %s -----------------------",
                    result.responseMetadata()));
            }
        } catch (UnsupportedEncodingException e) {
        } catch (Exception ex) {
            throw new RuntimeException(
                String.format("----------------------- Cannot publish to S3: %s -----------------------", ex.getMessage()), ex);
        } finally {
            if (null != tempFile) {
                try {
                    tempFile.delete();
                    tempFile = null;
                } catch (Exception ex) {
                }
            }
        }
    }

    static OutputStream createCompressedStreamAsNecessary(
        OutputStream outputStream, boolean compressEnabled) throws IOException {
        Objects.requireNonNull(outputStream);
        if (compressEnabled) {
            // System.out.println("Content will be compressed.");
            return new GZIPOutputStream(outputStream);
        } else {
            return outputStream;
        }
    }
}
