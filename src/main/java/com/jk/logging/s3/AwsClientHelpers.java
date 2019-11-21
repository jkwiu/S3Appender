package com.jk.logging.s3;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

public class AwsClientHelpers {

    // local credential을 불러올 것이다.
    public static S3Client buildClient(
        String accessKey, String secretKey, String sessionToken,
        Region region,
        String serviceEndpoint, String signingRegion
    ) {
        return S3Client.create();
    }
}
