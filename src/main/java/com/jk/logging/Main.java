package com.jk.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

import java.util.concurrent.TimeUnit;

/**
 * Example program to exercise the logger.  Together with the log4j.properties
 * in this project, this program will demonstrate logging batches into S3.
 * <br>
 * The program will basically loop for a period of time while logging messages in various
 * levels at small intervals to simulate typical logging patterns in a long-lived process.
 *
 * This class is designed to work with Log4j 2.x.
 *
 * @author vly
 */
public class Main {
    private static Logger logger =
        LogManager.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {

        // 이 부분의 로그는 log4j2.xml에 아래 메서드를 실행하는 클래스를 등록해놨으니 출력이 된다.
        // 이런 식으로 사용하면 된다.
        S3Client s3 = S3Client.builder().build();
		ListBucketsRequest listBucketsRequest = ListBucketsRequest.builder().build();
	    ListBucketsResponse listBucketsResponse = s3.listBuckets(listBucketsRequest);
	    listBucketsResponse.buckets().stream().forEach(x -> System.out.println(x.name()));

        logger.info("----------------------- Log Test Start!!!! -----------------------");
        Long started = System.currentTimeMillis();
        Long now = System.currentTimeMillis();
        // Loop for 3 minutes
        while (now - started < TimeUnit.MINUTES.toMillis(3)) {
            logger.info("info: JKJKJKJKJKJKJKJKJKJKJKJKJK");
            logger.warn("warn: JKJKJKJKJKJKJKJKJKJKJKJKJKJKJKJKJK");
            logger.error("error: JKJKJKJKJKJKJKJKJKJKJKJKJKJKJKJK");
            // Sleep for 7 seconds before logging messages again so we don't produce too much data
            Thread.sleep(TimeUnit.SECONDS.toMillis(7));
        }
        logger.info("----------------------- Finally it's finished -----------------------");
    }
}
