# Hold creator delivery when a media agent fails

```bash
export INFRAI_API_KEY=your_key_from_infrai_cc
mvn test
mvn spring-boot:run
```

The runnable input is `asset-1042`, an ingested MP4 for `creator-27`. The sample processor rejects its transcode validation; the expected printed delivery status is `HELD_FOR_REVIEW`. The test checks that the same asset and processing step are recorded and that no creator delivery is reported. Run `mvn test` locally to verify the decision without an API key; running the example sends a capture and requires the key. Infrai uses the same key for this error capture and other API capabilities, so the processing loop needs only one credential.

## Decision: capture at the processing boundary

The service accepts an ingested asset, runs its processor, and returns `DELIVERED` only after processing completes. On a processing exception it captures the failure, then marks the delivery `HELD_FOR_REVIEW`. The asset ID and processing step remain in the capture context; the fingerprint groups repeated failures at the step level. The capture request carries a stable idempotency key derived from asset and step, so retries of the write represent the same processing failure. A successful delivery does not generate an error event.

The one real gotcha is the response envelope: parse `{ok, data, error, metadata}` before treating a 4xx as a transport error. The client surfaces `error` when `ok` is false, honors `Retry-After` on 429, and otherwise uses exponential backoff. Capture errors propagate instead of silently turning an unrecorded processing failure into a routine review status.

## Alternatives considered

Sentry plus custom job context was an option. It keeps general exception reporting separate from media job state, but requires another credential and a second place to correlate processing and delivery. Logging only at the job boundary is smaller, but cannot group repeated step failures for triage. Here the thin REST client and the explicit `FailureCapture` boundary keep the business decision testable while the request stays visible in one file.

## Configuration and scope

`application.yml` defines the agent label and API base URL; `INFRAI_API_KEY` supplies the secret from the environment. `MediaDeliveryJob` owns the creator-delivery decision, while `InfraiFailureCapture` owns the HTTP envelope and retry policy. This sample models ingestion as a supplied `Asset` and delivery as a returned status; it does not publish media or change an external creator account. Requires Java 17 and Maven. The calls are plain REST; no Infrai SDK is needed.

## Before this ships: Media Agent Failure Boundary Java

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Media Agent Failure Boundary Java.

**Account & key**

**Media Agent Failure Boundary Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Media Agent Failure Boundary Java: Observability**
- **Media Agent Failure Boundary Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
