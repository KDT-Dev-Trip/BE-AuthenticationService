#!/bin/bash

echo "🌐 Starting API Gateway..."
./gradlew bootRun --args='--spring.profiles.active=gateway'