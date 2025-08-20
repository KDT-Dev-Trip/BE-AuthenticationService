-- DevOps 교육 플랫폼 데이터베이스 스키마
CREATE DATABASE IF NOT EXISTS devops_platform_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE devops_platform_db;

-- 1. 사용자 관리 테이블
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    auth0_id VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    role ENUM('STUDENT', 'INSTRUCTOR', 'ADMIN') NOT NULL DEFAULT 'STUDENT',
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    profile_image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    
    INDEX idx_auth0_id (auth0_id),
    INDEX idx_email (email),
    INDEX idx_role_status (role, status)
);

-- 2. 팀 관리 테이블
CREATE TABLE teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    team_code VARCHAR(20) UNIQUE NOT NULL,
    instructor_id BIGINT NOT NULL,
    max_members INT NOT NULL DEFAULT 6,
    current_members INT NOT NULL DEFAULT 0,
    status ENUM('ACTIVE', 'INACTIVE', 'COMPLETED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (instructor_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_team_code (team_code),
    INDEX idx_instructor (instructor_id),
    INDEX idx_status (status)
);

-- 3. 팀 멤버십 테이블
CREATE TABLE team_memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('LEADER', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_team_user (team_id, user_id),
    INDEX idx_team_id (team_id),
    INDEX idx_user_id (user_id)
);

-- 4. 미션/과제 관리 테이블
CREATE TABLE missions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    difficulty ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED') NOT NULL,
    category ENUM('AWS', 'DOCKER', 'KUBERNETES', 'CI_CD', 'TERRAFORM', 'MONITORING', 'SECURITY') NOT NULL,
    points INT NOT NULL DEFAULT 100,
    time_limit_hours INT,
    instruction_url VARCHAR(500),
    template_repository_url VARCHAR(500),
    evaluation_criteria JSON,
    prerequisites JSON,
    resources JSON,
    status ENUM('DRAFT', 'ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_category_difficulty (category, difficulty),
    INDEX idx_status (status),
    INDEX idx_created_by (created_by),
    FULLTEXT KEY ft_title_description (title, description)
);

-- 5. 미션 제출 테이블
CREATE TABLE mission_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    submitted_by BIGINT NOT NULL,
    submission_url VARCHAR(500),
    github_repository_url VARCHAR(500),
    deployment_url VARCHAR(500),
    submission_notes TEXT,
    status ENUM('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'RESUBMIT_REQUIRED') NOT NULL DEFAULT 'SUBMITTED',
    score INT,
    max_score INT,
    feedback TEXT,
    reviewed_by BIGINT,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    
    FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    FOREIGN KEY (submitted_by) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uk_mission_team (mission_id, team_id),
    INDEX idx_status (status),
    INDEX idx_submitted_at (submitted_at)
);

-- 6. AWS 환경 관리 테이블
CREATE TABLE aws_environments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    environment_name VARCHAR(100) NOT NULL,
    aws_account_id VARCHAR(20),
    region VARCHAR(20) NOT NULL DEFAULT 'ap-northeast-2',
    vpc_id VARCHAR(50),
    subnet_ids JSON,
    security_group_ids JSON,
    iam_role_arn VARCHAR(200),
    status ENUM('CREATING', 'ACTIVE', 'DELETING', 'DELETED', 'ERROR') NOT NULL DEFAULT 'CREATING',
    configuration JSON,
    resource_limits JSON,
    cost_budget_usd DECIMAL(10, 2),
    current_cost_usd DECIMAL(10, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    UNIQUE KEY uk_team_environment (team_id, environment_name),
    INDEX idx_status (status),
    INDEX idx_expires_at (expires_at)
);

-- 7. 환경 사용 로그 테이블
CREATE TABLE environment_usage_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    environment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action ENUM('CREATE', 'START', 'STOP', 'DELETE', 'ACCESS', 'DEPLOY') NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    details JSON,
    cost_impact DECIMAL(8, 4) DEFAULT 0.0000,
    logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (environment_id) REFERENCES aws_environments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_environment_user (environment_id, user_id),
    INDEX idx_logged_at (logged_at),
    INDEX idx_action (action)
);

-- 8. 학습 진도 관리 테이블
CREATE TABLE learning_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mission_id BIGINT NOT NULL,
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED') NOT NULL DEFAULT 'NOT_STARTED',
    progress_percentage INT NOT NULL DEFAULT 0,
    time_spent_minutes INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_mission (user_id, mission_id),
    INDEX idx_status (status),
    INDEX idx_progress (progress_percentage)
);

-- 9. 결제 및 구독 관리 테이블
CREATE TABLE subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_type ENUM('FREE', 'BASIC', 'PRO', 'ENTERPRISE') NOT NULL DEFAULT 'FREE',
    status ENUM('ACTIVE', 'CANCELLED', 'EXPIRED', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    billing_cycle ENUM('MONTHLY', 'YEARLY') NOT NULL DEFAULT 'MONTHLY',
    amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'KRW',
    stripe_subscription_id VARCHAR(100),
    current_period_start TIMESTAMP,
    current_period_end TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_subscription (user_id),
    INDEX idx_status (status),
    INDEX idx_expires (current_period_end)
);

-- 10. 알림 관리 테이블
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type ENUM('MISSION_ASSIGNED', 'SUBMISSION_REVIEWED', 'TEAM_INVITE', 'ENVIRONMENT_EXPIRING', 'PAYMENT_DUE', 'SYSTEM_ANNOUNCEMENT') NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    data JSON,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    priority ENUM('LOW', 'MEDIUM', 'HIGH', 'URGENT') NOT NULL DEFAULT 'MEDIUM',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_unread (user_id, is_read),
    INDEX idx_created_at (created_at),
    INDEX idx_type_priority (type, priority)
);

-- 11. 시스템 설정 테이블
CREATE TABLE system_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT NOT NULL,
    data_type ENUM('STRING', 'INTEGER', 'BOOLEAN', 'JSON') NOT NULL DEFAULT 'STRING',
    description TEXT,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_setting_key (setting_key),
    INDEX idx_is_public (is_public)
);

-- 12. 감사 로그 테이블
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    action ENUM('CREATE', 'READ', 'UPDATE', 'DELETE') NOT NULL,
    old_values JSON,
    new_values JSON,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_user_action (user_id, action),
    INDEX idx_entity (entity_type, entity_id),
    INDEX idx_created_at (created_at)
);

-- 13. 파일 관리 테이블
CREATE TABLE file_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    s3_bucket VARCHAR(100),
    s3_key VARCHAR(500),
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_entity (entity_type, entity_id),
    INDEX idx_uploaded_by (uploaded_by),
    INDEX idx_uploaded_at (uploaded_at)
);

-- 14. 시스템 메트릭 테이블
CREATE TABLE system_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(15, 4) NOT NULL,
    metric_unit VARCHAR(20),
    tags JSON,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_metric_name_time (metric_name, recorded_at),
    INDEX idx_recorded_at (recorded_at)
);

-- 초기 데이터 삽입

-- 시스템 설정 초기값
INSERT INTO system_settings (setting_key, setting_value, data_type, description, is_public) VALUES
('platform_name', 'DevOps Education Platform', 'STRING', '플랫폼 이름', true),
('max_team_members', '6', 'INTEGER', '팀 최대 멤버 수', true),
('default_aws_region', 'ap-northeast-2', 'STRING', '기본 AWS 리전', true),
('environment_default_expiry_hours', '168', 'INTEGER', '환경 기본 만료 시간 (시간 단위)', false),
('max_concurrent_environments_per_team', '3', 'INTEGER', '팀당 동시 환경 최대 개수', false),
('notification_email_enabled', 'true', 'BOOLEAN', '이메일 알림 활성화', false),
('cost_alert_threshold_usd', '100.00', 'STRING', '비용 알림 임계값 (USD)', false);

-- 샘플 관리자 사용자 추가 (미션 생성을 위해 필요)
INSERT INTO users (auth0_id, email, name, role, status) VALUES
('auth0|admin001', 'admin@devops-platform.com', '시스템 관리자', 'ADMIN', 'ACTIVE');

-- 샘플 미션 데이터
INSERT INTO missions (title, description, difficulty, category, points, time_limit_hours, created_by) VALUES
('AWS EC2 인스턴스 생성 및 관리', 'EC2 인스턴스를 생성하고 기본적인 웹 서버를 배포하는 미션입니다.', 'BEGINNER', 'AWS', 100, 4, 1),
('Docker 컨테이너화', '간단한 웹 애플리케이션을 Docker 컨테이너로 패키징하는 미션입니다.', 'BEGINNER', 'DOCKER', 150, 6, 1),
('Kubernetes 클러스터 배포', 'Kubernetes 클러스터에 애플리케이션을 배포하고 스케일링하는 미션입니다.', 'INTERMEDIATE', 'KUBERNETES', 250, 8, 1),
('CI/CD 파이프라인 구축', 'GitHub Actions를 사용한 CI/CD 파이프라인을 구축하는 미션입니다.', 'INTERMEDIATE', 'CI_CD', 200, 6, 1),
('Terraform을 이용한 IaC', 'Terraform을 사용하여 AWS 인프라를 코드로 관리하는 미션입니다.', 'ADVANCED', 'TERRAFORM', 300, 10, 1);

COMMIT;