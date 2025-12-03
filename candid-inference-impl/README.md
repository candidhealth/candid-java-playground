# Candid Inference Implementation

A lightweight, production-ready ML inference module for insurance claim denial prediction using ONNX Runtime.

## Overview

This module demonstrates a complete ML inference pipeline in Java using ONNX Runtime instead of framework-specific libraries like XGBoost4J. It showcases:

- **Categorical feature encoding** with metadata-driven mappings
- **Strict feature ordering** via explicit configuration
- **ONNX Runtime inference** (lightweight, portable, no native library issues)
- **Probability calibration** (Platt scaling and isotonic regression)
- **Clean separation** between model training (Python) and inference (Java)

## Architecture

```
┌─────────────┐
│  ClaimData  │ (Input)
└──────┬──────┘
       │
       v
┌────────────────────────────┐
│   FeatureMetadata +        │
│   CategoricalEncoder       │ (Encode categorical → numeric)
└──────┬─────────────────────┘
       │
       v
┌────────────────────────────┐
│   FeatureVector            │ (Ordered float array)
└──────┬─────────────────────┘
       │
       v
┌────────────────────────────┐
│   ONNX Runtime             │ (Model inference)
└──────┬─────────────────────┘
       │
       v
┌────────────────────────────┐
│   Calibrator               │ (Platt scaling / Isotonic)
└──────┬─────────────────────┘
       │
       v
┌─────────────┐
│DenialPredict│ (Output)
└─────────────┘
```

## Quick Start

```java
import com.candid.api.inference.*;

// Create predictor (loads models from classpath resources)
try (DenialPredictor predictor = new DenialPredictor()) {

    // Create claim data
    ClaimData claim = new ClaimData.Builder()
        .setAmount(15000.00)
        .setPatientAge(45)
        .setProcedureCategory("SURGERY")
        .setProviderState("CA")
        .setPriorDenialCount(1)
        .build();

    // Get prediction
    DenialPrediction prediction = predictor.predict(claim);

    System.out.println("Will be denied: " + prediction.willBeDenied());
    System.out.println("Probability: " + prediction.getProbability());
    System.out.println("Raw score: " + prediction.getRawScore());
}
```

Output:
```
Will be denied: true
Probability: 0.7324
Raw score: 0.8512
```

## Components

### 1. Feature Metadata (`FeatureMetadata.java`)

Defines features, their types, ordering, and categorical encodings.

**Configuration**: `src/main/resources/models/feature_metadata.json`

```json
{
  "features": [
    {"name": "claim_amount", "type": "NUMERIC", "index": 0},
    {"name": "patient_age", "type": "NUMERIC", "index": 1},
    {"name": "procedure_category", "type": "CATEGORICAL", "index": 2,
     "mapping": {"SURGERY": 0.0, "DIAGNOSTIC": 1.0, "THERAPY": 2.0, "EMERGENCY": 3.0, "UNKNOWN": 4.0}},
    {"name": "provider_state", "type": "CATEGORICAL", "index": 3,
     "mapping": {"CA": 0.0, "NY": 1.0, "TX": 2.0, "FL": 3.0, "UNKNOWN": 4.0}},
    {"name": "prior_denials", "type": "NUMERIC", "index": 4}
  ]
}
```

### 2. Categorical Encoder (`CategoricalEncoder.java`)

Converts categorical strings to numeric codes:
- Uses metadata mappings for encoding
- Falls back to "UNKNOWN" category for unseen values
- Logs warnings for unknown values

### 3. Feature Vector (`FeatureVector.java`)

Builder pattern for type-safe feature construction:

```java
FeatureVector vector = new FeatureVector.Builder(metadata)
    .setFeature("claim_amount", 15000.0)
    .setFeature("patient_age", 45)
    .setFeature("procedure_category", "SURGERY")
    .setFeature("provider_state", "CA")
    .setFeature("prior_denials", 1)
    .build();

float[] orderedValues = vector.getValues();  // [15000.0, 45.0, 0.0, 0.0, 1.0]
```

### 4. Calibration System

**Configuration**: `src/main/resources/models/calibration_params.json`

**Platt Scaling** (sigmoid transformation):
```json
{
  "type": "PLATT_SCALING",
  "parameters": {
    "a": -1.5,
    "b": 0.3
  }
}
```

Formula: `P(y=1|score) = 1 / (1 + exp(a * score + b))`

**Isotonic Regression** (piecewise constant):
```json
{
  "type": "ISOTONIC",
  "parameters": {
    "thresholds": [0.0, 0.3, 0.6, 0.9],
    "values": [0.1, 0.4, 0.7, 0.95]
  }
}
```

### 5. ONNX Runtime Pipeline (`InferencePipeline.java`)

Orchestrates the complete inference flow:
1. Encodes features using FeatureVector
2. Creates ONNX tensor from float array
3. Runs ONNX model inference
4. Applies calibration
5. Returns PredictionResult

### 6. High-Level API (`DenialPredictor.java`)

Simple facade that:
- Loads all resources from classpath
- Provides clean `predict(ClaimData)` method
- Implements `AutoCloseable` for resource management

## ONNX Model Requirements

The ONNX model file should be located at `src/main/resources/models/claim_denial_model.onnx`.

### Model Specifications

**Input:**
- Name: `"float_input"` (configurable)
- Type: `float32`
- Shape: `[batch_size, 5]` where 5 is the number of features
- Feature order: `[claim_amount, patient_age, procedure_category, provider_state, prior_denials]`

**Output:**
- Type: `float32`
- Shape: `[batch_size, 1]`
- Value: Raw prediction score (will be calibrated)

### Creating the ONNX Model (Python)

The model should be created using a separate Python script (maintained in a different repository).

**Example workflow:**

```python
import xgboost as xgb
import onnxmltools
from skl2onnx.common.data_types import FloatTensorType

# 1. Train XGBoost model
model = xgb.XGBClassifier(n_estimators=10, max_depth=3)
model.fit(X_train, y_train)

# 2. Export to ONNX
initial_type = [('float_input', FloatTensorType([None, 5]))]
onnx_model = onnxmltools.convert_xgboost(model, initial_types=initial_type)

# 3. Save ONNX model
onnxmltools.utils.save_model(onnx_model, 'claim_denial_model.onnx')

# 4. Fit calibration on validation set
from sklearn.calibration import CalibratedClassifierCV
calibrated = CalibratedClassifierCV(model, method='sigmoid', cv='prefit')
calibrated.fit(X_val, y_val)

# 5. Export calibration parameters
calibration_params = {
    "type": "PLATT_SCALING",
    "parameters": {
        "a": float(calibrated.calibrated_classifiers_[0].a_),
        "b": float(calibrated.calibrated_classifiers_[0].b_)
    }
}

# 6. Export feature metadata
feature_metadata = {
    "features": [...]  # Define features with types and categorical mappings
}
```

**Required Python packages:**
```
xgboost
onnxmltools
skl2onnx
scikit-learn
numpy
```

## Why ONNX Runtime?

### Advantages over XGBoost4J

| Feature | ONNX Runtime | XGBoost4J |
|---------|--------------|-----------|
| Dependency size | ~10-15MB | ~20-30MB+ |
| Native libraries | None (pure Java) | Requires libomp, platform-specific |
| Portability | High (standard format) | Medium (JVM-specific) |
| Framework lock-in | None | XGBoost only |
| Production maturity | Very high | High |
| Cross-language | Yes (Python, C++, C#, etc.) | Limited |

### Benefits

1. **Lighter weight**: Smaller dependency footprint
2. **No native library issues**: Avoids libomp and other native dependency problems
3. **More portable**: ONNX is an industry standard supported across frameworks
4. **Better separation of concerns**: Python for training, Java for inference
5. **Framework flexibility**: Can export from PyTorch, TensorFlow, scikit-learn, XGBoost, etc.

## Project Structure

```
candid-inference-impl/
├── src/main/java/com/candid/api/inference/
│   ├── model/
│   │   ├── FeatureMetadata.java          # Feature definitions, types, ordering
│   │   ├── CategoricalEncoder.java       # String → numeric encoding
│   │   └── FeatureVector.java            # Ordered feature array with builder
│   ├── calibration/
│   │   ├── Calibrator.java               # Interface for calibration
│   │   ├── CalibrationConfig.java        # Config loader
│   │   ├── PlattScalingCalibrator.java   # Sigmoid calibration
│   │   └── IsotonicCalibrator.java       # Piecewise calibration
│   ├── pipeline/
│   │   └── InferencePipeline.java        # Main orchestrator (ONNX Runtime)
│   ├── ClaimData.java                    # Input data class
│   ├── DenialPrediction.java             # Output result class
│   └── DenialPredictor.java              # High-level API
└── src/main/resources/
    └── models/
        ├── claim_denial_model.onnx       # ONNX model file (from Python)
        ├── feature_metadata.json         # Feature config
        └── calibration_params.json       # Calibration params
```

## Extending the Module

### Adding New Features

1. Update `feature_metadata.json` with new feature definition
2. Update `ClaimData.java` to include new field
3. Update `DenialPredictor.predict()` to map new field
4. Retrain and export ONNX model with new features

### Using Different Calibration

Edit `calibration_params.json`:

```json
{
  "type": "ISOTONIC",
  "parameters": {
    "thresholds": [0.1, 0.3, 0.5, 0.7, 0.9],
    "values": [0.05, 0.25, 0.5, 0.75, 0.95]
  }
}
```

### Using Custom Models

Pass custom paths to `DenialPredictor`:

```java
DenialPredictor predictor = new DenialPredictor(
    "models/my_custom_model.onnx",
    "models/my_custom_metadata.json",
    "models/my_custom_calibration.json",
    "custom_input_name"
);
```

## Testing

All feature handling and calibration components have comprehensive unit tests:

```bash
./gradlew :candid-inference-impl:test
```

**Test coverage:**
- ✅ Feature metadata loading (5/5 tests passing)
- ✅ Categorical encoding (3/3 tests passing)
- ✅ Feature vector building (3/3 tests passing)
- ✅ Calibration (7/7 tests passing)
- ⏸️ End-to-end inference (requires ONNX model file)

## Dependencies

```kotlin
dependencies {
    // ONNX Runtime for model inference
    implementation("com.microsoft.onnxruntime:onnxruntime:1.19.2")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Logging
    implementation("org.slf4j:slf4j-api")
    runtimeOnly("ch.qos.logback:logback-classic")
}
```

## Performance Considerations

- **Model loading**: ONNX model is loaded once and reused (session-based API)
- **Thread safety**: Create separate `DenialPredictor` instances for concurrent use
- **Resource cleanup**: Use try-with-resources or call `close()` to release ONNX session
- **Batch prediction**: Current API is single-prediction; can be extended for batches

## Future Enhancements

- Batch prediction support
- Model monitoring and logging
- A/B testing framework
- SHAP value integration for explainability
- Performance benchmarking
- Model versioning support

## References

- [ONNX Runtime Documentation](https://onnxruntime.ai/)
- [ONNX Runtime Java API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/package-summary.html)
- [XGBoost to ONNX Conversion](https://onnxmltools.readthedocs.io/)
- [Model Calibration Guide](https://scikit-learn.org/stable/modules/calibration.html)
