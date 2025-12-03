"""
ONNX Model Export Template for Candid Inference

This script demonstrates how to train an XGBoost model and export it to ONNX format
along with the required metadata files for the Java inference module.

NOTE: This script should live in a separate Python repository for model training.
It is included here as documentation/reference only.

Requirements:
    pip install xgboost onnxmltools skl2onnx scikit-learn numpy pandas

Usage:
    python export_model.py

Outputs:
    - claim_denial_model.onnx
    - feature_metadata.json
    - calibration_params.json
"""

import json
import numpy as np
import xgboost as xgb
import onnxmltools
from sklearn.calibration import CalibratedClassifierCV
from sklearn.model_selection import train_test_split
from skl2onnx.common.data_types import FloatTensorType


def create_sample_data():
    """
    Create sample training data for demonstration.
    In production, replace this with your actual data pipeline.
    """
    np.random.seed(42)
    n_samples = 1000

    # Generate synthetic features
    claim_amounts = np.random.uniform(1000, 50000, n_samples)
    patient_ages = np.random.randint(18, 90, n_samples)

    # Categorical features (encoded as integers for training)
    procedure_categories = np.random.randint(0, 4, n_samples)  # 0-3: SURGERY, DIAGNOSTIC, THERAPY, EMERGENCY
    provider_states = np.random.randint(0, 4, n_samples)  # 0-3: CA, NY, TX, FL
    prior_denials = np.random.randint(0, 5, n_samples)

    # Combine features
    X = np.column_stack([
        claim_amounts,
        patient_ages,
        procedure_categories,
        provider_states,
        prior_denials
    ])

    # Generate labels (higher amounts and more prior denials increase denial probability)
    denial_prob = (claim_amounts > 20000).astype(float) * 0.5 + (prior_denials > 2).astype(float) * 0.3
    y = (np.random.random(n_samples) < denial_prob).astype(int)

    return X.astype(np.float32), y


def train_model(X_train, y_train):
    """
    Train XGBoost classifier.
    Customize hyperparameters as needed.
    """
    model = xgb.XGBClassifier(
        n_estimators=10,
        max_depth=3,
        learning_rate=0.1,
        random_state=42,
        use_label_encoder=False,
        eval_metric='logloss'
    )

    model.fit(X_train, y_train)
    return model


def export_to_onnx(model, output_path='claim_denial_model.onnx'):
    """
    Export XGBoost model to ONNX format.

    Args:
        model: Trained XGBoost model
        output_path: Path to save ONNX model

    Returns:
        Path to saved ONNX model
    """
    # Define input type - CRITICAL: must match Java expectations
    # Shape: [None, 5] means variable batch size, 5 features
    initial_type = [('float_input', FloatTensorType([None, 5]))]

    # Convert to ONNX
    onnx_model = onnxmltools.convert_xgboost(
        model,
        initial_types=initial_type,
        target_opset=12  # Use a stable ONNX opset version
    )

    # Save ONNX model
    onnxmltools.utils.save_model(onnx_model, output_path)
    print(f"✓ ONNX model saved to: {output_path}")

    return output_path


def export_feature_metadata(output_path='feature_metadata.json'):
    """
    Export feature metadata JSON for Java inference.

    This metadata ensures:
    - Features are processed in the correct order
    - Categorical values are encoded consistently
    - Unknown categorical values have a fallback
    """
    metadata = {
        "features": [
            {
                "name": "claim_amount",
                "type": "NUMERIC",
                "index": 0
            },
            {
                "name": "patient_age",
                "type": "NUMERIC",
                "index": 1
            },
            {
                "name": "procedure_category",
                "type": "CATEGORICAL",
                "index": 2,
                "mapping": {
                    "SURGERY": 0.0,
                    "DIAGNOSTIC": 1.0,
                    "THERAPY": 2.0,
                    "EMERGENCY": 3.0,
                    "UNKNOWN": 4.0  # Fallback for unseen values
                }
            },
            {
                "name": "provider_state",
                "type": "CATEGORICAL",
                "index": 3,
                "mapping": {
                    "CA": 0.0,
                    "NY": 1.0,
                    "TX": 2.0,
                    "FL": 3.0,
                    "UNKNOWN": 4.0  # Fallback for unseen values
                }
            },
            {
                "name": "prior_denials",
                "type": "NUMERIC",
                "index": 4
            }
        ]
    }

    with open(output_path, 'w') as f:
        json.dump(metadata, f, indent=2)

    print(f"✓ Feature metadata saved to: {output_path}")
    return output_path


def calibrate_and_export(model, X_val, y_val, output_path='calibration_params.json'):
    """
    Calibrate model and export calibration parameters.

    Calibration improves probability estimates by fitting a calibration function
    on a validation set.

    Args:
        model: Trained model
        X_val: Validation features
        y_val: Validation labels
        output_path: Path to save calibration params
    """
    # Fit calibration (Platt scaling = sigmoid)
    calibrated = CalibratedClassifierCV(
        model,
        method='sigmoid',  # Platt scaling
        cv='prefit'  # Use pre-trained model
    )
    calibrated.fit(X_val, y_val)

    # Extract calibration parameters
    # These are the a and b parameters in: P = 1 / (1 + exp(a * score + b))
    calibrator = calibrated.calibrated_classifiers_[0]

    calibration_params = {
        "type": "PLATT_SCALING",
        "parameters": {
            "a": float(calibrator.a_),
            "b": float(calibrator.b_)
        }
    }

    with open(output_path, 'w') as f:
        json.dump(calibration_params, f, indent=2)

    print(f"✓ Calibration parameters saved to: {output_path}")
    print(f"  Platt scaling: a={calibrator.a_:.4f}, b={calibrator.b_:.4f}")

    return output_path


def main():
    """
    Main export pipeline.
    """
    print("=" * 60)
    print("XGBoost to ONNX Export Pipeline")
    print("=" * 60)

    # 1. Create/load data
    print("\n1. Creating sample data...")
    X, y = create_sample_data()
    X_train, X_temp, y_train, y_temp = train_test_split(X, y, test_size=0.3, random_state=42)
    X_val, X_test, y_val, y_test = train_test_split(X_temp, y_temp, test_size=0.5, random_state=42)

    print(f"   Train: {len(X_train)} samples")
    print(f"   Val:   {len(X_val)} samples")
    print(f"   Test:  {len(X_test)} samples")

    # 2. Train model
    print("\n2. Training XGBoost model...")
    model = train_model(X_train, y_train)

    # Evaluate
    train_acc = model.score(X_train, y_train)
    test_acc = model.score(X_test, y_test)
    print(f"   Train accuracy: {train_acc:.4f}")
    print(f"   Test accuracy:  {test_acc:.4f}")

    # 3. Export to ONNX
    print("\n3. Exporting to ONNX...")
    export_to_onnx(model)

    # 4. Export feature metadata
    print("\n4. Exporting feature metadata...")
    export_feature_metadata()

    # 5. Calibrate and export
    print("\n5. Calibrating model and exporting parameters...")
    calibrate_and_export(model, X_val, y_val)

    print("\n" + "=" * 60)
    print("Export complete!")
    print("=" * 60)
    print("\nGenerated files:")
    print("  1. claim_denial_model.onnx       - ONNX model file")
    print("  2. feature_metadata.json         - Feature definitions")
    print("  3. calibration_params.json       - Calibration parameters")
    print("\nNext steps:")
    print("  1. Copy these files to:")
    print("     candid-inference-impl/src/main/resources/models/")
    print("  2. Run Java inference:")
    print("     ./gradlew :candid-inference-impl:build")
    print("  3. Test predictions:")
    print("     See README.md for usage examples")
    print("=" * 60)


if __name__ == "__main__":
    main()
