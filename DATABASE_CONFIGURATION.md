# DevTrip MSA 데이터베이스 설정 가이드

## 1. 데이터베이스 구조 개요

DevTrip MSA는 각 서비스별로 독립적인 데이터베이스를 사용하여 서비스 간 결합도를 최소화하고 확장성을 보장합니다.

### 1.1 데이터베이스 명명 규칙
```
devtrip-{service-name}
```

### 1.2 현재 데이터베이스 목록
| 서비스명 | 데이터베이스명 | 포트 | 설명 |
|---------|---------------|------|------|
| Authentication | `devtrip-authentication` | 8080 | 사용자 인증, JWT, OAuth2 |
| Payment | `devtrip-payment` | 8081 | 결제 처리, 구독 관리 |
| User Management | `devtrip-user-mgmt` | 8082 | 사용자 정보, 팀 관리 |
| Mission Management | `devtrip-mission-mgmt` | 8083 | 미션 데이터, 진행상황 |
| AI Evaluation | `devtrip-ai-evaluation` | 8084 | AI 평가 결과, 분석 데이터 |
| Monitoring | `devtrip-monitoring` | 8085 | 모니터링 메트릭, 로그 |

## 2. 데이터베이스 연결 설정

### 2.1 공통 MySQL 설정
```yaml
# Docker Compose 설정
services:
  devtrip-mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: devtrip_root_password
      MYSQL_USER: devtrip_user
      MYSQL_PASSWORD: devtrip_password
    ports:
      - "3306:3306"
```

### 2.2 서비스별 연결 문자열 설정

#### Authentication Service
```properties
# application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/devtrip-authentication?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=devtrip_user
spring.datasource.password=devtrip_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

#### Payment Service
```properties
# application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/devtrip-payment?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=devtrip_user
spring.datasource.password=devtrip_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

#### User Management Service
```properties
# application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/devtrip-user-mgmt?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=devtrip_user
spring.datasource.password=devtrip_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

#### Mission Management Service
```properties
# application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/devtrip-mission-mgmt?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=devtrip_user
spring.datasource.password=devtrip_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

#### AI Evaluation Service
```properties
# application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/devtrip-ai-evaluation?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=devtrip_user
spring.datasource.password=devtrip_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

#### Monitoring Service (개발 예정)
```properties
# application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/devtrip-monitoring?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=devtrip_user
spring.datasource.password=devtrip_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

## 3. JPA/Hibernate 설정

### 3.1 공통 JPA 설정
```properties
# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false
spring.jpa.open-in-view=false

# Connection Pool 설정
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

### 3.2 환경별 설정

#### 로컬 개발 환경
```properties
# application-local.properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

#### 개발 환경
```properties
# application-dev.properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
```

#### 운영 환경
```properties
# application-prod.properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

## 4. 데이터베이스 마이그레이션

### 4.1 Flyway 설정 (권장)
```properties
# Flyway 마이그레이션 설정
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.validate-on-migrate=true
```

### 4.2 마이그레이션 스크립트 예시
```sql
-- db/migration/V1__Create_user_table.sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 5. 환경변수 설정

### 5.1 Docker Compose 환경변수
```yaml
# docker-compose.infrastructure.yml
services:
  service-name:
    environment:
      # Database
      DB_HOST: devtrip-mysql
      DB_PORT: 3306
      DB_NAME: devtrip-{service-name}
      DB_USERNAME: devtrip_user
      DB_PASSWORD: devtrip_password
```

### 5.2 Kubernetes 환경변수
```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: devtrip-db-config
data:
  DB_HOST: "devtrip-mysql-service"
  DB_PORT: "3306"
  DB_USERNAME: "devtrip_user"
---
apiVersion: v1
kind: Secret
metadata:
  name: devtrip-db-secret
type: Opaque
stringData:
  DB_PASSWORD: "devtrip_password"
```

## 6. 데이터베이스 모니터링

### 6.1 연결 풀 모니터링
```properties
# Actuator를 통한 데이터베이스 모니터링
management.endpoints.web.exposure.include=health,metrics,info,beans
management.endpoint.health.show-details=always
management.metrics.export.prometheus.enabled=true
```

### 6.2 슬로우 쿼리 모니터링
```properties
# MySQL 슬로우 쿼리 설정
spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS=100
```

## 7. 백업 및 복구 전략

### 7.1 데이터베이스 백업 스크립트
```bash
#!/bin/bash
# backup-databases.sh

BACKUP_DIR="/var/backups/devtrip"
DATE=$(date +%Y%m%d_%H%M%S)

databases=("devtrip-authentication" "devtrip-payment" "devtrip-user-mgmt" "devtrip-mission-mgmt" "devtrip-ai-evaluation" "devtrip-monitoring")

for db in "${databases[@]}"; do
    echo "Backing up $db..."
    docker exec devtrip-mysql mysqldump -u devtrip_user -pdevtrip_password $db > $BACKUP_DIR/${db}_${DATE}.sql
done

echo "Backup completed!"
```

### 7.2 데이터베이스 복구 스크립트
```bash
#!/bin/bash
# restore-database.sh

DB_NAME=$1
BACKUP_FILE=$2

if [ -z "$DB_NAME" ] || [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <database_name> <backup_file>"
    exit 1
fi

echo "Restoring $DB_NAME from $BACKUP_FILE..."
docker exec -i devtrip-mysql mysql -u devtrip_user -pdevtrip_password $DB_NAME < $BACKUP_FILE
echo "Restore completed!"
```

## 8. 트러블슈팅

### 8.1 일반적인 문제와 해결책

#### 연결 거부 오류
```bash
# MySQL 컨테이너 상태 확인
docker logs devtrip-mysql

# 네트워크 연결 테스트
telnet localhost 3306
```

#### 권한 오류
```sql
-- MySQL 권한 재설정
GRANT ALL PRIVILEGES ON `devtrip-%`.* TO 'devtrip_user'@'%';
FLUSH PRIVILEGES;
```

#### 데이터베이스 초기화
```bash
# 데이터베이스 재생성
docker-compose -f docker-compose.infrastructure.yml down -v
docker-compose -f docker-compose.infrastructure.yml up -d
```

### 8.2 성능 최적화

#### 인덱스 최적화
```sql
-- 자주 사용하는 쿼리에 인덱스 추가
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_created_at ON users(created_at);
```

#### 커넥션 풀 튜닝
```properties
# HikariCP 최적화
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.validation-timeout=5000
```

## 9. 보안 설정

### 9.1 데이터베이스 보안
```properties
# SSL 연결 활성화 (운영환경)
spring.datasource.url=jdbc:mysql://localhost:3306/devtrip-service?useSSL=true&requireSSL=true&verifyServerCertificate=true

# 연결 암호화
spring.datasource.url=jdbc:mysql://localhost:3306/devtrip-service?useSSL=true&characterEncoding=UTF-8&serverTimezone=UTC
```

### 9.2 비밀번호 관리
```yaml
# Kubernetes Secret 사용
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
type: Opaque
stringData:
  username: devtrip_user
  password: "복잡한_비밀번호_여기에"
```

## 10. 체크리스트

### 10.1 설정 완료 체크리스트
- [ ] 모든 서비스별 데이터베이스 생성 확인
- [ ] 각 서비스의 application.properties 업데이트
- [ ] JPA/Hibernate 설정 확인
- [ ] 환경변수 설정 완료
- [ ] 마이그레이션 스크립트 준비
- [ ] 백업 전략 수립
- [ ] 모니터링 설정 완료
- [ ] 보안 설정 적용

### 10.2 테스트 체크리스트
- [ ] 각 서비스 데이터베이스 연결 테스트
- [ ] CRUD 작업 테스트
- [ ] 트랜잭션 롤백 테스트
- [ ] 연결 풀 동작 확인
- [ ] 성능 테스트
- [ ] 백업/복구 테스트