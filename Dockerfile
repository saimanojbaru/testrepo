FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy project files
COPY pyproject.toml poetry.lock* ./
COPY . .

# Install Python dependencies
RUN pip install --upgrade pip && \
    pip install -e .

# Create logs directory
RUN mkdir -p logs

EXPOSE 8000

CMD ["python", "-m", "main"]
