# ☁️ Cloud Foundry Deployment Guide

## 📋 Overview

This guide covers deploying the IMC Policy MCP Server to Cloud Foundry with the atomic profile architecture, connecting to the required AI and database services.

## 🎯 Required Services

Before deploying, ensure these services are available in your Cloud Foundry space:

### 1️⃣ **AI Services**
```bash
# Create or verify AI services exist
cf services | grep -E "(embed-model|chat-model)"

# If not available, create user-provided services:
cf create-user-provided-service embed-model -p '{"api_key":"your-openai-key","base_url":"https://api.openai.com"}'
cf create-user-provided-service chat-model -p '{"api_key":"your-openai-key","base_url":"https://api.openai.com"}'
```

### 2️⃣ **Database Service**
```bash
# Create PostgreSQL service with pgvector support
cf create-service postgres xlarge embed-db -c '{"extensions": ["pgvector"]}'

# Or verify existing service
cf service embed-db
```

## 🚀 Deployment Steps

### 1️⃣ **Build the Application**
```bash
# Build the standard Spring Boot JAR file.
# The buildpack will be specified in the manifest.yml
mvn clean package -DskipTests
```

### 2️⃣ **Deploy to Cloud Foundry**
```bash
# Deploy using the manifest
cf push -f manifest.yml

# Monitor deployment
cf logs imc-policy-mcp-server --recent
```

### 3️⃣ **Verify Deployment**
```bash
# Check application status
cf app imc-policy-mcp-server

# Test health endpoint
curl https://imc-policy-mcp-server.apps.your-domain.com/actuator/health

# Check service bindings
cf env imc-policy-mcp-server
```

## 🔧 Configuration Options

### **Atomic Profile Combinations**

The manifest supports different AI model configurations through atomic profiles:

#### **Pure Cloud (Default)**
```yaml
env:
  SPRING_PROFILES_ACTIVE: "cloud,chat-openai,embed-openai"
```
- ✅ Maximum reliability
- ✅ Latest AI models
- ❌ Higher costs

#### **Hybrid Configuration**
```yaml
env:
  SPRING_PROFILES_ACTIVE: "cloud,chat-openai,embed-ollama"
  OLLAMA_BASE_URL: "http://ollama-service.apps.internal:11434"
```
- ✅ Cost-effective embeddings
- ✅ Reliable chat
- ⚠️ Requires Ollama service on CF

### **Environment-Specific Configurations**

#### **Production**
```yaml
instances: 1
memory: 2G
env:
  SPRING_PROFILES_ACTIVE: "cloud,chat-openai,embed-openai"
  LOGGING_LEVEL_ROOT: "WARN"
```

#### **Staging**
```yaml
instances: 1
memory: 1G
env:
  SPRING_PROFILES_ACTIVE: "cloud,chat-openai,embed-openai"
  LOGGING_LEVEL_ROOT: "INFO"
```

#### **Development**
```yaml
instances: 1
memory: 512M
env:
  SPRING_PROFILES_ACTIVE: "cloud,chat-openai,embed-openai"
  LOGGING_LEVEL_ROOT: "DEBUG"
```

## 🔒 Security Considerations

### **Service Credentials**
```bash
# AI service credentials are automatically bound via CF services
# Database credentials are injected via VCAP_SERVICES

# For custom credentials, use user-provided services:
cf create-user-provided-service my-openai -p '{
  "api_key": "your-secret-key",
  "base_url": "https://api.openai.com",
  "model_chat": "gpt-4.1-nano",
  "model_embedding": "text-embedding-3-small"
}'
```

### **Network Security**
```yaml
# Internal routing (recommended for backend services)
routes:
  - route: imc-policy-mcp-server.apps.internal

# Public routing (if needed for external access)
routes:
  - route: imc-policy-mcp-server.apps.your-domain.com
```

## 📊 Monitoring & Observability

### **Health Checks**
```bash
# Application health
curl https://imc-policy-mcp-server.apps.internal/actuator/health

# Detailed health with credentials
curl -u admin:secret https://imc-policy-mcp-server.apps.internal/actuator/health
```

### **Logging**
```bash
# Stream logs
cf logs imc-policy-mcp-server

# Recent logs
cf logs imc-policy-mcp-server --recent

# Filter by component
cf logs imc-policy-mcp-server | grep "AI Model"
```

### **Metrics**
```bash
# Application metrics
curl https://imc-policy-mcp-server.apps.internal/actuator/metrics

# Custom metrics
curl https://imc-policy-mcp-server.apps.internal/actuator/metrics/spring.ai.mcp.requests
```

## 🛠️ Troubleshooting

### **Common Issues**

#### **Service Binding Failures**
```bash
# Check service status
cf service embed-db
cf service embed-model  
cf service chat-model

# Recreate bindings
cf unbind-service imc-policy-mcp-server embed-db
cf bind-service imc-policy-mcp-server embed-db
cf restart imc-policy-mcp-server
```

#### **Memory Issues**
```bash
# Increase memory allocation
cf scale imc-policy-mcp-server -m 3G

# Check memory usage
cf app imc-policy-mcp-server
```

#### **AI Model Connection Issues**
```bash
# Check service credentials
cf env imc-policy-mcp-server | grep -A 10 VCAP_SERVICES

# Test AI connectivity
curl -X POST https://imc-policy-mcp-server.apps.internal/tools/testAIModels
```

## 🔄 Updates & Rollbacks

### **Zero-Downtime Deployment**
```bash
# Blue-green deployment
cf push imc-policy-mcp-server-green -f manifest.yml --no-route
cf map-route imc-policy-mcp-server-green apps.your-domain.com --hostname imc-policy-mcp-server
cf unmap-route imc-policy-mcp-server apps.your-domain.com --hostname imc-policy-mcp-server
cf delete imc-policy-mcp-server -f
cf rename imc-policy-mcp-server-green imc-policy-mcp-server
```

### **Rollback**
```bash
# Quick rollback to previous version
cf rollback imc-policy-mcp-server

# Manual rollback
cf push imc-policy-mcp-server -f manifest-previous.yml
```

## 📈 Scaling

### **Horizontal Scaling**
```bash
# Scale to 3 instances
cf scale imc-policy-mcp-server -i 3

# Auto-scaling (if available)
cf create-service app-autoscaler standard autoscaler
cf bind-service imc-policy-mcp-server autoscaler
```

### **Vertical Scaling**
```bash
# Increase memory and disk
cf scale imc-policy-mcp-server -m 4G -k 2G
```

## 🔗 Integration

### **Service Discovery**
```bash
# Register with service registry (if available)
cf create-service service-registry standard registry
cf bind-service imc-policy-mcp-server registry
```

### **API Gateway Integration**
```yaml
# Route through API Gateway
routes:
  - route: api-gateway.apps.your-domain.com/mcp/policy
```

This deployment configuration leverages the atomic profile architecture to provide maximum flexibility in your Cloud Foundry environment!