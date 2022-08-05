package org.lambda.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import org.lambda.model.Clients;
import org.lambda.model.DynamoDBTable;
import org.lambda.model.Request;
import org.lambda.model.Response;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;

import static org.lambda.model.CommonConstants.*;

public class DynamoDBCheckList implements RequestHandler<Request, List<Response>> {

    public Clients createClients(AssumeRoleResponse assumeRoleResponse) {

        String accessKey = assumeRoleResponse.credentials().accessKeyId();
        String secretKey = assumeRoleResponse.credentials().secretAccessKey();
        String sessionToken = assumeRoleResponse.credentials().sessionToken();

        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(accessKey,secretKey,sessionToken);

        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .region(Region.of(DEFAULT_REGION))
                .build();

        S3Client s3 = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .region(Region.of(DEFAULT_REGION))
                .build();

        DynamoDbEnhancedClient dynamoDbEnhancedClient =
                DynamoDbEnhancedClient.builder()
                        .dynamoDbClient(dynamoDbClient)
                        .build();

        ApplicationAutoScalingClient autoScalingClient =
                ApplicationAutoScalingClient
                        .builder()
                        .region(Region.of(DEFAULT_REGION))
                        .build();

        Ec2Client ec2Client =
                Ec2Client
                        .builder()
                        .region(Region.of(DEFAULT_REGION))
                        .build();

        return new Clients(s3,dynamoDbClient,dynamoDbEnhancedClient,autoScalingClient,ec2Client);

    }

    public AssumeRoleResponse assumeGivenRole(StsClient stsClient, String roleArn, String roleSessionName) throws StsException {
        AssumeRoleRequest roleRequest = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName(roleSessionName)
                .build();

        AssumeRoleResponse roleResponse = stsClient.assumeRole(roleRequest);
        Credentials myCreds = roleResponse.credentials();
        // Display the time when the temp creds expire
        Instant exTime = myCreds.expiration();
        // Convert the Instant to readable date
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.US)
                .withZone(ZoneId.systemDefault());

        formatter.format(exTime);
        return roleResponse;
    }

    public String get_account_number(AssumeRoleResponse assume_role_object) {
        Region region = Region.AP_SOUTH_1;
        String accessKey = assume_role_object.credentials().accessKeyId();
        String secretKey = assume_role_object.credentials().secretAccessKey();
        String sessionToken = assume_role_object.credentials().sessionToken();
        String account;
        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(accessKey, secretKey, sessionToken);
        if (!S3_WEB_REPORT_OBFUSCATE_ACCOUNT) {
            StsClient stsClient = StsClient.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                    .region(region)
                    .build();
            account = stsClient.getCallerIdentity(GetCallerIdentityRequest.builder().build()).account();
        }
        else {
            account = "111111111111";
        }
        return account;
    }

    void updateScanStatus(DynamoDbEnhancedClient dynamoDbEnhancedClient, String scanId, String accountId, String scanStatus) {
        try {
            DynamoDbTable<DynamoDBTable> table = dynamoDbEnhancedClient.table(DYNAMODB_TABLE, TableSchema.fromBean(DynamoDBTable.class));
            Key key = Key.builder()
                    .partitionValue(scanId)
                    .build();

            DynamoDBTable dynamoDBTable = table.getItem(r -> r.key(key));
            dynamoDBTable.setAwsAccountId(accountId);
            dynamoDBTable.setScanStatus(scanStatus);
            table.updateItem(dynamoDBTable);
        } catch (Exception exception) {
            System.out.println("Unable to update scan status in DB:992");
        }
    }

    void updateReportStatus(DynamoDbEnhancedClient dynamoDbEnhancedClient, String scanId, String report_bucket, String report_key) {
        try {
            DynamoDbTable<DynamoDBTable> table = dynamoDbEnhancedClient.table(DYNAMODB_TABLE, TableSchema.fromBean(DynamoDBTable.class));
            Key key = Key.builder()
                    .partitionValue(scanId)
                    .build();

            DynamoDBTable dynamoDBTable = table.getItem(r -> r.key(key));
            dynamoDBTable.getReportBucket(report_bucket);
            dynamoDBTable.getReportKey(report_key);
            table.updateItem(dynamoDBTable);
        } catch (Exception exception) {
            System.out.println("Unable to update scan status in DB:992");
        }
    }

    String s3Report(DynamoDbEnhancedClient dynamoDB, S3Client s3, List<org.lambda.model.Response> control, String accountNumber, Request event) {
        String scanId = event.scanId;
        String json = new Gson().toJson(control);
        String reportName;
        if (S3_WEB_REPORT_NAME_DETAILS)
            reportName = scanId + "-DynamoDB_REPORT" + ".json";
        else
            reportName = "DynamoDB_REPORT.json";
        String S3_key = "Swayam/Compliance/DynamoDB/" + accountNumber + "/" + DEFAULT_REGION + '/' + reportName;
        try {
            Path tempPath = Files.createTempFile(null,null);
            Files.write(tempPath, json.getBytes(StandardCharsets.UTF_8));
            File tempFile = tempPath.toFile();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3_JSON_REPORT_BUCKET)
                    .key(S3_key)
                    .build();
            s3.putObject(putObjectRequest, RequestBody.fromFile(tempFile));

            tempFile.deleteOnExit();

        } catch (IOException e) {
            return "Failed to upload report to S3 because: " + e.getMessage();
        }
        updateReportStatus(dynamoDB, scanId, S3_JSON_REPORT_BUCKET, S3_key);
        Date expiration = new Date();
        long expTimeMillis = Instant.now().toEpochMilli();
        expTimeMillis += S3_JSON_REPORT_EXPIRE * 60L;
        expiration.setTime(expTimeMillis);

        S3Presigner presigner = S3Presigner.create();

        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(S3_JSON_REPORT_BUCKET)
                .key(S3_key)
                .build();

        GetObjectPresignRequest getObjectPresignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(objectRequest)
                        .build();

        PresignedGetObjectRequest presignedGetObjectRequest =
                presigner.presignGetObject(getObjectPresignRequest);

        String url = String.valueOf(s3.getObject(getObjectPresignRequest.getObjectRequest()));
        System.out.println(presignedGetObjectRequest.url());
        return url;
    }
    void sendResultsSNS(String url) {
        String region = (SNS_TOPIC_ARN.split("sns:", 1)[1]).split(":", 1)[0];
        SnsClient snsClient = SnsClient.builder().region(Region.of(region)).build();
        String pattern = "E MMM dd HH:mm:ss yyyy";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        String timeNow = simpleDateFormat.format(new Date());
        PublishRequest publishRequest = PublishRequest.builder()
                .topicArn(SNS_TOPIC_ARN)
                .message("default: " + url + "}")
                .subject("AWS CIS Benchmark report - " + timeNow)
                .messageStructure("json")
                .build();
        snsClient.publish(publishRequest);
    }

    @Override
    public List<Response> handleRequest(Request request, Context context) {
        String accountNumber = null;
        SdkHttpClient httpClient = ApacheHttpClient
                .builder()
                .build();
        StsClient stsClient = StsClient.builder()
                .httpClient(httpClient)
                .build();
        String roleSessionName = "MsOpsAssumeRoleSession";
        String accountId = request.getAccountId();
        String roleArn = "arn:aws:iam::" + accountId + ":role/SwayamParth"; //msopsstsrole
        AssumeRoleResponse assume_role_object = assumeGivenRole(stsClient, roleArn, roleSessionName);
        Clients clients = createClients(assume_role_object);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            accountNumber = get_account_number(assume_role_object);
        } catch (Exception exception) {
            updateReportStatus(clients.getDbEnhancedClient(), request.scanId, accountId, exception.getMessage());
        }
        updateScanStatus(clients.getDbEnhancedClient(), request.scanId, accountId, "SCANNING");
        System.out.println("Assumed account Id: " + accountNumber);

        List<Response> controls = new ArrayList<>();
        try {

            controls.add(control_1_1_kms_encryption(clients.getDbClient()));
            controls.add(control_1_3_continuous_backup(clients.getDbClient()));
            controls.add(control_1_4_vpc_endpoints(clients.getEc2Client()));
            controls.add(control_1_5_unused_dynamodb_tables(clients.getDbClient()));

        }
        catch (Exception exception ) {
            System.out.println("Error while creating DynamoDB Audit: " + exception.getMessage());
        }
        updateScanStatus(clients.getDbEnhancedClient(), request.scanId, accountId,"COMPLETE");
        List<Response> json_report = new ArrayList<>();

        //build JSON structure for console output if enabled

        if ( S3_JSON_REPORT ) {
            String signedURL = s3Report(clients.getDbEnhancedClient(), clients.getS3Client(),json_report,accountNumber,request);
            if (!OUTPUT_ONLY_JSON)
                System.out.println("SignedURL:\n" + signedURL);
            if (SEND_REPORT_URL_TO_SNS)
                sendResultsSNS(signedURL);
        }
        return controls;
    }

    public Response control_1_1_kms_encryption(DynamoDbClient dbClient) {
        String result = "true";
        String failReason = "";
        List<String> offenders = new ArrayList<>();
        String control = "1.1";
        String description = "AWS DynamoDB Tables Should Use KMS CMKs for Encryption.";
        Boolean scored = true;
        String processDescription = "";
        String severity = "High";
        String category = "";

        ListTablesResponse tablesResponse = dbClient.listTables();
        List<String> tables = tablesResponse.tableNames();
        DescribeTableRequest tableRequest =
                DescribeTableRequest
                        .builder()
                        .tableName(String.valueOf(tables))
                        .build();

        DescribeTableResponse tableResponse = dbClient.describeTable(tableRequest);

        if (!tableResponse.table().sseDescription().sseType().equals("KMS")) {

            result = "false";
            failReason = "KMS encryption is not enabled for AWS DynamoDB Tables."
                    + "Amazon DynamoDB tables should be using AWS-managed Customer Master "
                    + "Keys (CMKs) instead of AWS-owned CMKs for Server-Side Encryption (SSE), in order to meet "
                    + "strict encryption compliance and regulatory requirements. DynamoDB supports to switch from "
                    + "AWS-owned CMKs to customer-managed CMKs managed using Amazon Key Management Service (KMS), without "
                    + "any code to encrypt the data.";
            offenders.add("Table Name: " + tablesResponse.tableNames());

        }


        return new Response(result, failReason, offenders, scored, description, control, processDescription, severity, category);
    }

    public Response control_1_3_continuous_backup(DynamoDbClient dbClient) {
        String result = "true";
        String failReason = "";
        List<String> offenders = new ArrayList<>();
        String control = "1.3";
        String description = "DynamoDB Tables Should Have Continuous Backup Enabled.";
        Boolean scored = true;
        String processDescription = "";
        String severity = "High";
        String category = "";

        ListTablesResponse tablesResponse = dbClient.listTables();
        List<String> tables = tablesResponse.tableNames();

        DescribeContinuousBackupsRequest backupsRequest =
                DescribeContinuousBackupsRequest
                        .builder()
                        .tableName(tables.toString())
                        .build();

        DescribeContinuousBackupsResponse backupsResponse = dbClient.describeContinuousBackups(backupsRequest);

        if(backupsResponse.continuousBackupsDescription().pointInTimeRecoveryDescription().pointInTimeRecoveryStatus().equals("DISABLED")) {

            result = "false";
            failReason = "Continuous Backup is not enabled for DynamoDB Tables."
                    + "DynamoDB table without backup can result in accidental data loss. Your AWS DynamoDB tables should make use of Point-in-time Recovery "
                    + "(PITR) feature in order to automatically take continuous backups of your DynamoDB data.";
            offenders.add("Table Name: " + tablesResponse.tableNames());

        }

        return new Response(result, failReason, offenders, scored, description, control, processDescription, severity, category);
    }

    public Response control_1_4_vpc_endpoints(Ec2Client ec2Client) {
        String result = "true";
        String failReason = "";
        List<String> offenders = new ArrayList<>();
        String control = "1.4";
        String description = "VPC Endpoint Should Be Enabled For DynamoDB.";
        Boolean scored = true;
        String processDescription = "";
        String severity = "Medium";
        String category = "";

        DescribeVpcsResponse vpcResponse = ec2Client.describeVpcs();

        List<Vpc> vpcs = vpcResponse.vpcs();

        for (Vpc vpc : vpcs ) {

            DescribeVpcEndpointsRequest vpcEndpointsRequest =
                    DescribeVpcEndpointsRequest
                            .builder()
                            .filters(
                                    Filter
                                            .builder()
                                            .name("vpc-id")
                                            .values(vpc.vpcId())
                                            .build())
                            .build();

            DescribeVpcEndpointsResponse vpcEndpointsResponse =
                    ec2Client.describeVpcEndpoints(vpcEndpointsRequest);

            List<VpcEndpoint> endpoints = vpcEndpointsResponse.vpcEndpoints();

            if (endpoints.isEmpty()) {

                result = "false";
                failReason = "VPC EndPoint is not Enabled for DynamoDB."
                        +"A VPC endpoint for DynamoDB enables Amazon EC2 instances in your "
                        +"VPC to use their private IP addresses to access DynamoDB with no exposure to the public "
                        +"internet. Your EC2 instances do not require public IP addresses, and you do not need an internet gateway, "
                        +"a NAT device, or a virtual private gateway in your VPC.";
                offenders.add("VPC Id: " + vpc.vpcId());

            }

        }

        return new Response(result, failReason, offenders, scored, description, control, processDescription, severity, category);
    }

    public Response control_1_5_unused_dynamodb_tables(DynamoDbClient dbClient) {
        String result = "true";
        String failReason = "";
        List<String> offenders = new ArrayList<>();
        String control = "1.5";
        String description = "AWS Account Should Not Have Any Unused DynamoDB Tables.";
        Boolean scored = true;
        String processDescription = "";
        String severity = "Medium";
        String category = "";

        ListTablesResponse tablesResponse = dbClient.listTables();
        List<String> tables = tablesResponse.tableNames();

        DescribeTableRequest tableRequest =
                DescribeTableRequest
                        .builder()
                        .tableName(tables.toString())
                        .build();

        DescribeTableResponse tableResponse = dbClient.describeTable(tableRequest);

        if (tableResponse.table().itemCount().equals(0)) {

            result = "false";
            failReason = "AWS Account is using Empty DynamoDB Tables."
                    + "Any unused Amazon DynamoDB tables available within your "
                    + "AWS account should be removed to help lower the cost of your monthly AWS bill.";
            offenders.add("Table Name: " + tablesResponse.tableNames());

        }

        return new Response(result, failReason, offenders, scored, description, control, processDescription, severity, category);
    }

}
