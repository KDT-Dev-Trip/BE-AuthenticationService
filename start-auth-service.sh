#!/bin/bash

echo "🔐 Starting Authentication Service..."
./gradlew bootRun --args='--spring.profiles.active=auth'