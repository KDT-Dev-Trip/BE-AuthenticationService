pipeline {
    agent any
    
    environment {
        // 환경 변수 설정
        DOCKER_REGISTRY = 'localhost:5000'
        IMAGE_NAME = 'authentication-service'
        APP_NAME = 'be-authentication-service'
        NAMESPACE = 'devops-platform'
        
        // Git 설정
        GIT_REPO = 'https://github.com/your-org/BE-AuthenticationService.git'
        GIT_BRANCH = 'main'
        
        // 도구 버전
        JAVA_VERSION = '17'
        GRADLE_VERSION = '8.10'
        KUBECTL_VERSION = '1.28.0'
        
        // 알림 설정
        SLACK_CHANNEL = '#devops-alerts'
        TEAMS_WEBHOOK = credentials('teams-webhook-url')
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        skipStagesAfterUnstable()
        parallelsAlwaysFailFast()
    }
    
    stages {
        stage('🚀 Pipeline Start') {
            steps {
                script {
                    echo "===================================================="
                    echo "🚀 Starting CI/CD Pipeline for ${APP_NAME}"
                    echo "📋 Build Number: ${BUILD_NUMBER}"
                    echo "🌿 Branch: ${GIT_BRANCH}"
                    echo "👤 Started by: ${BUILD_USER}"
                    echo "===================================================="
                    
                    // Slack 알림
                    slackSend(
                        channel: SLACK_CHANNEL,
                        color: 'good',
                        message: "🚀 *${APP_NAME}* CI/CD Pipeline Started\\n• Build: #${BUILD_NUMBER}\\n• Branch: ${GIT_BRANCH}\\n• Triggered by: ${BUILD_USER}"
                    )
                }
            }
        }
        
        stage('📦 Checkout & Setup') {
            parallel {
                stage('Git Checkout') {
                    steps {
                        checkout scm
                        script {
                            env.GIT_COMMIT_SHORT = sh(
                                script: 'git rev-parse --short HEAD',
                                returnStdout: true
                            ).trim()
                            env.BUILD_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                            echo "📦 Checked out commit: ${env.GIT_COMMIT_SHORT}"
                        }
                    }
                }
                
                stage('Environment Setup') {
                    steps {
                        script {
                            // Java 버전 확인
                            sh 'java -version'
                            sh './gradlew --version'
                            
                            // Docker 환경 확인
                            sh 'docker --version'
                            sh 'docker-compose --version'
                            
                            echo "✅ Environment setup complete"
                        }
                    }
                }
            }
        }
        
        stage('🔍 Code Quality Analysis') {
            parallel {
                stage('Static Code Analysis') {
                    steps {
                        script {
                            echo "🔍 Running static code analysis..."
                            
                            // SpotBugs, PMD, Checkstyle
                            sh './gradlew check spotbugsMain pmdMain checkstyleMain'
                            
                            // SonarQube 분석 (선택사항)
                            // sh './gradlew sonarqube'
                        }
                        
                        publishHTML([
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'build/reports/spotbugs',
                            reportFiles: 'main.html',
                            reportName: 'SpotBugs Report'
                        ])
                    }
                }
                
                stage('Dependency Check') {
                    steps {
                        script {
                            echo "🔍 Checking dependencies for vulnerabilities..."
                            
                            // OWASP Dependency Check
                            sh './gradlew dependencyCheckAnalyze'
                        }
                        
                        publishHTML([
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'build/reports',
                            reportFiles: 'dependency-check-report.html',
                            reportName: 'OWASP Dependency Check'
                        ])
                    }
                }
            }
        }
        
        stage('🏗️ Build & Test') {
            parallel {
                stage('Gradle Build') {
                    steps {
                        script {
                            echo "🏗️ Building application..."
                            
                            // Gradle 빌드
                            sh './gradlew clean build -x test'
                            
                            // 빌드 아티팩트 확인
                            sh 'ls -la build/libs/'
                        }
                        
                        // 빌드 아티팩트 보관
                        archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: false
                    }
                }
                
                stage('Unit Tests') {
                    steps {
                        script {
                            echo "🧪 Running unit tests..."
                            
                            // 단위 테스트 실행
                            sh './gradlew test'
                        }
                        
                        // 테스트 결과 발행
                        publishTestResults testResultsPattern: 'build/test-results/test/*.xml'
                        
                        // 코드 커버리지 리포트
                        publishHTML([
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'build/reports/jacoco/test/html',
                            reportFiles: 'index.html',
                            reportName: 'JaCoCo Coverage Report'
                        ])
                    }
                }
                
                stage('Integration Tests') {
                    steps {
                        script {
                            echo "🧪 Running integration tests..."
                            
                            // Docker Compose로 테스트 환경 구성
                            sh 'docker-compose -f docker-compose.test.yml up -d'
                            
                            // 통합 테스트 실행
                            sh './gradlew integrationTest'
                            
                            // 테스트 환경 정리
                            sh 'docker-compose -f docker-compose.test.yml down -v'
                        }
                    }
                }
            }
        }
        
        stage('🐳 Docker Build') {
            steps {
                script {
                    echo "🐳 Building Docker image..."
                    
                    // Docker 이미지 빌드
                    def image = docker.build("${DOCKER_REGISTRY}/${IMAGE_NAME}:${BUILD_TAG}")
                    
                    // 최신 태그도 생성
                    sh "docker tag ${DOCKER_REGISTRY}/${IMAGE_NAME}:${BUILD_TAG} ${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
                    
                    // 이미지 크기 확인
                    sh "docker images | grep ${IMAGE_NAME}"
                    
                    env.DOCKER_IMAGE = "${DOCKER_REGISTRY}/${IMAGE_NAME}:${BUILD_TAG}"
                }
            }
        }
        
        stage('🔒 Security Scan') {
            parallel {
                stage('Container Security Scan') {
                    steps {
                        script {
                            echo "🔒 Scanning Docker image for vulnerabilities..."
                            
                            // Trivy를 사용한 컨테이너 보안 스캔
                            sh """
                                trivy image --exit-code 0 --severity LOW,MEDIUM --format table ${env.DOCKER_IMAGE}
                                trivy image --exit-code 1 --severity HIGH,CRITICAL --format table ${env.DOCKER_IMAGE}
                            """
                        }
                    }
                }
                
                stage('Application Security Test') {
                    steps {
                        script {
                            echo "🔒 Running application security tests..."
                            
                            // OWASP ZAP을 사용한 보안 테스트 (선택사항)
                            // sh 'zap-baseline.py -t http://localhost:8080'
                        }
                    }
                }
            }
        }
        
        stage('📤 Push to Registry') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    echo "📤 Pushing Docker image to registry..."
                    
                    // Docker 레지스트리에 푸시
                    docker.withRegistry("http://${DOCKER_REGISTRY}") {
                        sh "docker push ${env.DOCKER_IMAGE}"
                        sh "docker push ${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
                    }
                    
                    echo "✅ Image pushed: ${env.DOCKER_IMAGE}"
                }
            }
        }
        
        stage('🚀 Deploy to Staging') {
            when {
                branch 'develop'
            }
            steps {
                script {
                    echo "🚀 Deploying to staging environment..."
                    
                    // ArgoCD를 통한 배포 트리거
                    sh """
                        curl -X POST \\
                          -H "Authorization: Bearer \${ARGOCD_TOKEN}" \\
                          -H "Content-Type: application/json" \\
                          -d '{"revision": "${env.GIT_COMMIT_SHORT}"}' \\
                          \${ARGOCD_SERVER}/api/v1/applications/${APP_NAME}-staging/sync
                    """
                }
            }
        }
        
        stage('🚀 Deploy to Production') {
            when {
                branch 'main'
            }
            steps {
                script {
                    // 프로덕션 배포 승인 요청
                    timeout(time: 10, unit: 'MINUTES') {
                        input message: '🚀 Deploy to Production?', 
                              ok: 'Deploy',
                              submitterParameter: 'DEPLOYER'
                    }
                    
                    echo "🚀 Deploying to production environment..."
                    echo "🙋‍♂️ Approved by: ${env.DEPLOYER}"
                    
                    // ArgoCD를 통한 프로덕션 배포
                    sh """
                        curl -X POST \\
                          -H "Authorization: Bearer \${ARGOCD_TOKEN}" \\
                          -H "Content-Type: application/json" \\
                          -d '{"revision": "${env.GIT_COMMIT_SHORT}"}' \\
                          \${ARGOCD_SERVER}/api/v1/applications/${APP_NAME}-production/sync
                    """
                }
            }
        }
        
        stage('✅ Post-Deploy Validation') {
            parallel {
                stage('Health Check') {
                    steps {
                        script {
                            echo "✅ Running health checks..."
                            
                            // 애플리케이션 헬스체크
                            sh """
                                timeout 300 bash -c 'until curl -f http://localhost:8080/actuator/health; do sleep 5; done'
                                curl -f http://localhost:8080/auth/health
                                curl -f http://localhost:8080/sso/status
                            """
                        }
                    }
                }
                
                stage('Smoke Tests') {
                    steps {
                        script {
                            echo "🔥 Running smoke tests..."
                            
                            // 기본적인 API 테스트
                            sh '''
                                # JWT 토큰 발급 테스트
                                response=$(curl -s -X POST http://localhost:8080/auth/login \\
                                  -H "Content-Type: application/json" \\
                                  -d '{"email":"test@example.com","password":"password123"}')
                                
                                echo "Login response: $response"
                                
                                # SSO 상태 확인
                                curl -f http://localhost:8080/sso/status
                            '''
                        }
                    }
                }
            }
        }
    }
    
    post {
        always {
            script {
                echo "🧹 Cleaning up workspace..."
                
                // Docker 이미지 정리
                sh "docker system prune -f"
                
                // 워크스페이스 정리
                cleanWs()
            }
        }
        
        success {
            script {
                echo "✅ Pipeline completed successfully!"
                
                // Slack 성공 알림
                slackSend(
                    channel: SLACK_CHANNEL,
                    color: 'good',
                    message: """
                        ✅ *${APP_NAME}* Pipeline SUCCESS!
                        • Build: #${BUILD_NUMBER}
                        • Branch: ${GIT_BRANCH}
                        • Commit: ${env.GIT_COMMIT_SHORT}
                        • Image: ${env.DOCKER_IMAGE}
                        • Duration: ${currentBuild.durationString}
                    """
                )
            }
        }
        
        failure {
            script {
                echo "❌ Pipeline failed!"
                
                // Slack 실패 알림
                slackSend(
                    channel: SLACK_CHANNEL,
                    color: 'danger',
                    message: """
                        ❌ *${APP_NAME}* Pipeline FAILED!
                        • Build: #${BUILD_NUMBER}
                        • Branch: ${GIT_BRANCH}
                        • Stage: ${env.STAGE_NAME}
                        • Duration: ${currentBuild.durationString}
                        • Console: ${BUILD_URL}console
                    """
                )
            }
        }
        
        unstable {
            script {
                echo "⚠️ Pipeline completed with warnings!"
                
                slackSend(
                    channel: SLACK_CHANNEL,
                    color: 'warning',
                    message: """
                        ⚠️ *${APP_NAME}* Pipeline UNSTABLE!
                        • Build: #${BUILD_NUMBER}
                        • Some tests may have failed
                        • Please check the results
                    """
                )
            }
        }
    }
}