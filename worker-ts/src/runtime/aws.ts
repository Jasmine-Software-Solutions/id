import { SSMClient } from "@aws-sdk/client-ssm";

/** Region resolution: an explicit SNS/SSM region wins over the Lambda-provided AWS_REGION. */
export function awsRegion(explicit?: string): string {
  return explicit?.trim() || process.env.AWS_REGION || "us-east-1";
}

export function createSsmClient(options: { region: string; endpoint?: string }): SSMClient {
  return new SSMClient({
    region: options.region,
    ...(options.endpoint ? { endpoint: options.endpoint } : {}),
  });
}
