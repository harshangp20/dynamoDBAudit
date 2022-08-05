package org.lambda.model;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;

@NoArgsConstructor
@AllArgsConstructor
@ToString
@Getter
@Setter
public class Clients {

    private S3Client s3Client;
    private DynamoDbClient dbClient;
    private DynamoDbEnhancedClient dbEnhancedClient;
    private ApplicationAutoScalingClient autoScalingClient;
    private Ec2Client ec2Client;

}
