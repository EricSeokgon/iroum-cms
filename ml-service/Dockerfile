FROM python:3.11-slim

WORKDIR /app

# 빌드 도구 (sentence-transformers/torch 휠 설치 안정화)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# sentence-transformers 모델 사전 다운로드 (런타임 오프라인 동작 보장)
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')"

COPY app/ ./app/

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=20s \
    CMD curl -fsS http://localhost:8000/ml/v1/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
