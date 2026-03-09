# Bridge Version Configuration

## Overview

The `acp-langraph-langchain-bridge` dependency version can be configured differently for local development vs CI/CD environments.

## Local Development

By default, the version is set to `1.0-SNAPSHOT` for local development. No configuration needed - just run:

```bash
./gradlew build
```

## CI/CD (GitHub Actions)

### ✅ Recommended: Workflow-Level Environment Variable

Define the `BRIDGE_VERSION` **once** at the workflow level, and it will be available to all jobs and steps:

```yaml
name: CI Build & Test

on:
  push:
    branches: [ main ]

env:
  BRIDGE_VERSION: "1.0.13"  # ✅ Defined ONCE here

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build
        run: ./gradlew build  # Automatically uses BRIDGE_VERSION

  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Test
        run: ./gradlew test  # Also uses BRIDGE_VERSION
```

**Benefits:**
- ✅ Define version **once** - no duplication
- ✅ Available to all jobs and steps automatically
- ✅ Easy to update in one place
- ✅ Clean and maintainable

### Alternative: Gradle Property

Pass the version as a Gradle property (requires repeating for each command):

```yaml
- name: Build
  run: ./gradlew build -PbridgeVersion=1.0.13
```

## How It Works

The `build.gradle.kts` file resolves the version in this priority order:

1. **Gradle property** (`-PbridgeVersion=X.X.X`) - highest priority
2. **Environment variable** (`BRIDGE_VERSION`) - medium priority
3. **Default value** (`1.0-SNAPSHOT`) - lowest priority (local development)

```kotlin
val bridgeVersion = (findProperty("bridgeVersion") as String?) 
    ?: System.getenv("BRIDGE_VERSION") 
    ?: "1.0-SNAPSHOT"
implementation("net.osgiliath.ai:acp-langraph-langchain-bridge:$bridgeVersion")
```

## Testing Locally

You can test the CI configuration locally:

```bash
# Using environment variable
BRIDGE_VERSION=1.0.13 ./gradlew build

# Using Gradle property
./gradlew build -PbridgeVersion=1.0.13
```

## Example

See `.github/workflows/ci.yml` for the complete working implementation.
