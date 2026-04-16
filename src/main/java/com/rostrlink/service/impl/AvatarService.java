package com.rostrlink.service.impl;

import java.time.Duration;

public class AvatarService {
    private final S3Presigner presigner;
    private final String bucket;

    public AvatarService(S3Presigner presigner, String bucket) {
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public PresignedUpload generatePresignedPut(String key, Duration expiry, String contentType) {
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .acl(ObjectCannedACL.PRIVATE)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .putObjectRequest(putReq)
                .signatureDuration(expiry)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return new PresignedUpload(presigned.url().toString(), key, presigned.signedHeaders());
    }
}

