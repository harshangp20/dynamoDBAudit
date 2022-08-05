package org.lambda.model;

import lombok.ToString;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
@ToString
public class DynamoDBTable {

    private String scanId;
    private String awsAccountId;
    private String reportBucket;
    private String reportKey;
    private String scanStatus;
    private int ttl;

    @DynamoDbPartitionKey
    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getAwsAccountId() {
        return awsAccountId;
    }

    public void setAwsAccountId(String awsAccountId) {
        this.awsAccountId = awsAccountId;
    }

    public String getReportBucket(String report_bucket) {
        return reportBucket;
    }

    public void setReportBucket(String reportBucket) {
        this.reportBucket = reportBucket;
    }

    public String getReportKey(String report_key) {
        return reportKey;
    }

    public void setReportKey(String reportKey) {
        this.reportKey = reportKey;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(String scanStatus) {
        this.scanStatus = scanStatus;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }
}
