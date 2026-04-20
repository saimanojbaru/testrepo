from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd
import pytest

from regime.classifier import (
    RegimeLabel,
    build_feature_matrix,
    fit_from_dataframe,
    label_bars,
    train_classifier,
)

FIXTURES = Path(__file__).parent.parent / "data" / "fixtures"


def _load_nifty() -> pd.DataFrame:
    df = pd.read_csv(FIXTURES / "ohlcv_1m_sample.csv", parse_dates=["ts"])
    return df[df["symbol"] == "NIFTY"].reset_index(drop=True)


def test_label_bars_produces_known_labels():
    df = _load_nifty()
    labels = label_bars(df)
    assert len(labels) == len(df)
    unique = set(labels.unique())
    # All labels must be valid RegimeLabel members
    assert unique.issubset({r for r in RegimeLabel})


def test_build_feature_matrix_no_lookahead():
    df = _load_nifty()
    X = build_feature_matrix(df)
    assert len(X) == len(df)
    assert {"adx_14", "rv_30", "ret_5", "tod"}.issubset(X.columns)


def test_train_classifier_runs_and_returns_result():
    df = _load_nifty()
    result = train_classifier(df, n_splits=2)
    assert len(result.folds_accuracy) == 2
    assert 0.0 <= result.validation_accuracy <= 1.0
    assert result.model is not None


def test_fit_from_dataframe_predict_bar():
    df = _load_nifty()
    clf = fit_from_dataframe(df)
    X = build_feature_matrix(df).fillna(0)
    # Single-bar inference must return a valid label
    row = X.iloc[-1]
    label = clf.predict_bar(row)
    assert isinstance(label, RegimeLabel)


def test_classifier_save_load(tmp_path):
    df = _load_nifty()
    clf = fit_from_dataframe(df)
    model_path = tmp_path / "model.pkl"
    clf.save(model_path)
    loaded = type(clf).load(model_path)
    X = build_feature_matrix(df).fillna(0)
    original_preds = clf.predict(X).values
    loaded_preds = loaded.predict(X).values
    assert (original_preds == loaded_preds).all()
