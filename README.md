# Contextual RAG Retrieval

This project demonstrates how to enhance RAG (Retrieval Augmented Generation) with Contextual Retrieval using Claude.

## Overview

The notebook `contextual_rag_retrieval.ipynb` walks through:

1. **Basic RAG** – Baseline vector search using Voyage AI embeddings
2. **Contextual Embeddings** – Claude generates situating context for each chunk before embedding, with prompt caching for cost efficiency
3. **Contextual BM25 Hybrid Search** – Combines semantic search with BM25 keyword search via Elasticsearch
4. **Reranking** – Uses Cohere's reranking model for a final precision boost

## Results

| Approach | Pass@5 | Pass@10 | Pass@20 |
|----------|--------|---------|---------|
| Baseline RAG | 80.92% | 87.15% | 90.06% |
| + Contextual Embeddings | 88.12% | 92.34% | 94.29% |
| + Hybrid Search (BM25) | 86.43% | 93.21% | 94.99% |
| + Reranking | 92.15% | 95.26% | 97.45% |

## Prerequisites

- Python 3.8+
- Docker (for Elasticsearch/BM25 search)
- API keys: `ANTHROPIC_API_KEY`, `VOYAGE_API_KEY`, `COHERE_API_KEY`

## Setup

```bash
pip install -r requirements.txt
```

For BM25 hybrid search, start Elasticsearch:

```bash
docker run -d --name elasticsearch -p 9200:9200 -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  elasticsearch:9.2.0
```

## Data

Place the following files in the `data/` directory before running the notebook:

- `data/codebase_chunks.json` – Pre-chunked codebase documents
- `data/evaluation_set.jsonl` – Evaluation queries with golden chunks

## Running

Open and run `contextual_rag_retrieval.ipynb` in Jupyter.
