# Candid Denial Predictor Python Client

Python client library for the Candid Denial Predictor gRPC service.

## Installation

```bash
pip install candid-denial-predictor-client
```

## Usage

```python
import grpc
from candid_api.denial_predictor_pb2 import DenialPredictionPayload
from candid_api.denial_predictor_pb2_grpc import DenialPredictorStub

# Create a gRPC channel
channel = grpc.insecure_channel('localhost:9090')

# Create a stub
stub = DenialPredictorStub(channel)

# Create a request
request = DenialPredictionPayload(
    patient_id="P12345",
    claim_amount=15000.00,
    procedure_code="CPT-99999"
)

# Make the RPC call
response = stub.predictDenial(request)

print(f"Will be denied: {response.will_be_denied}")
print(f"Confidence: {response.confidence_score}")
print(f"Reason: {response.reason}")
```

## Development

This package is auto-generated from Protocol Buffer definitions.
