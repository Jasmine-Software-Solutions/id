# Lambda container image for the ID worker functions.
#
# Assets are built on the host first (scripts/bundle.mjs), mirroring the
# Gradle-then-Docker flow used by the JVM worker: the image only copies
# artifacts, so the image build itself needs no npm registry access.

FROM public.ecr.aws/lambda/nodejs:24-arm64

COPY dist ${LAMBDA_TASK_ROOT}/dist
COPY proto ${LAMBDA_TASK_ROOT}/proto

# One image serves all three functions; each function overrides the command
# via `image_config.command` in Terraform.
CMD ["dist/handlers/email.handler"]
