package org.lambda.model;

import java.text.SimpleDateFormat;

public class CommonConstants {

    public static final String DEFAULT_REGION = "ap-south-1";
    public static boolean S3_WEB_REPORT_OBFUSCATE_ACCOUNT = false;
    public static String DYNAMODB_TABLE = "MsOpsAudit";
    public static boolean S3_WEB_REPORT_NAME_DETAILS = true;
    public static String S3_JSON_REPORT_BUCKET = "msops-audit-reports-parth";
    public static int S3_JSON_REPORT_EXPIRE = 168;
    public static String SNS_TOPIC_ARN = "CHANGE_ME_TO_YOUR_TOPIC_ARN";
    public static SimpleDateFormat formatter = new SimpleDateFormat("YYYY-MM-DD'T'HH:mm:ss.SSS'Z'");
    public static boolean S3_JSON_REPORT = true;
    public static boolean OUTPUT_ONLY_JSON = false;
    public static boolean SEND_REPORT_URL_TO_SNS = false;

}
