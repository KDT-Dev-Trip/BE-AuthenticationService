-- ============================================================================
-- 핵심 비즈니스 테이블 (Mission Management Service) - BIGINT userId 통일 버전
-- ============================================================================

-- 미션 테이블 (컨테이너 및 리소스 정보 포함)
DROP TABLE IF EXISTS mission CASCADE;
CREATE TABLE mission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '미션 고유 식별자',
    title VARCHAR(200) NOT NULL COMMENT '미션 제목',
    description TEXT NOT NULL COMMENT '미션 상세 설명',
    difficulty VARCHAR(255) NOT NULL COMMENT '난이도 (BEGINNER, INTERMEDIATE, ADVANCED)',
    category VARCHAR(255) NOT NULL COMMENT '카테고리 (DOCKER, KUBERNETES, CI_CD 등)',
    points INT NOT NULL DEFAULT 100 COMMENT '완료 시 얻는 포인트',
    time_limit_hours INT COMMENT '제한 시간 (시간 단위, NULL이면 무제한)',
    mission_guide LONGTEXT COMMENT '통합 미션 가이드 (마크다운 형태)',
    evaluation_criteria JSON COMMENT '평가 기준 (JSON 형태)',
    prerequisites JSON COMMENT '사전 요구사항 (JSON 형태)',
    container_image VARCHAR(200) DEFAULT 'ubuntu:22.04' COMMENT '컨테이너 이미지',
    container_tools JSON COMMENT '설치할 도구들 (JSON 배열)',
    cpu_request VARCHAR(20) DEFAULT '100m' COMMENT 'CPU 요청',
    cpu_limit VARCHAR(20) DEFAULT '500m' COMMENT 'CPU 제한',
    memory_request VARCHAR(20) DEFAULT '256Mi' COMMENT '메모리 요청',
    memory_limit VARCHAR(20) DEFAULT '1Gi' COMMENT '메모리 제한',
    storage_limit VARCHAR(20) DEFAULT '5Gi' COMMENT '저장공간 제한',
    session_timeout_minutes INT DEFAULT 480 COMMENT '세션 타임아웃 (분)',
    status VARCHAR(255) NOT NULL DEFAULT 'DRAFT' COMMENT '미션 상태 (DRAFT, ACTIVE, ARCHIVED)',
    created_by BIGINT NOT NULL COMMENT '미션 생성자 ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '미션 생성 시간',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '미션 마지막 수정 시간'
);

-- 미션 시도 테이블 (Kubernetes 리소스 관리 포함)
DROP TABLE IF EXISTS mission_attempt CASCADE;
CREATE TABLE mission_attempt (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '미션 시도 고유 식별자 (UUID)',
    user_id BIGINT NOT NULL COMMENT '시도하는 사용자 ID',
    mission_id VARCHAR(36) NOT NULL COMMENT '시도하는 미션 ID (mission.id 참조)',
    environment_id VARCHAR(36) NULL COMMENT '할당된 개발 환경 ID (다른 서비스에서 관리)',
    status VARCHAR(255) NOT NULL COMMENT '시도 상태 (STARTED, IN_PROGRESS, COMPLETED, FAILED, EXPIRED)',
    submission_data TEXT NULL COMMENT '제출된 작업 내용 (JSON 형태)',
    evaluation_result TEXT NULL COMMENT '자동 평가 결과 (JSON 형태)',
    score INT NOT NULL DEFAULT 0 COMMENT '얻은 점수',
    tickets_used INT NOT NULL DEFAULT 1 COMMENT '이 시도에 사용된 티켓 수',
    stamp_earned BOOLEAN NOT NULL DEFAULT FALSE COMMENT '스탬프 획득 여부 (성공 시 true)',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '미션 시작 시간',
    completed_at TIMESTAMP NULL COMMENT '미션 완료 시간',
    expires_at TIMESTAMP NOT NULL COMMENT '미션 만료 시간',
    paused_at TIMESTAMP NULL COMMENT '일시정지 시점',
    resource_id VARCHAR(255) NULL COMMENT '리소스 ID',
    resource_name VARCHAR(255) NULL COMMENT '리소스 이름',
    pod_ip VARCHAR(45) NULL COMMENT 'Pod IP 주소',
    service_port INT NULL COMMENT '서비스 포트',
    websocket_url VARCHAR(500) NULL COMMENT 'WebSocket URL',
    ssh_host VARCHAR(255) NULL COMMENT 'SSH 호스트',
    ssh_port INT NULL COMMENT 'SSH 포트',
    -- Kubernetes PV/PVC 관련 필드
    pvc_name VARCHAR(100) NULL COMMENT 'PVC 이름',
    deployment_name VARCHAR(100) NULL COMMENT 'Kubernetes Deployment 이름',
    storage_size VARCHAR(20) NULL COMMENT '할당된 스토리지 크기 (예: 5Gi)',
    storage_class VARCHAR(50) NULL COMMENT '사용된 StorageClass',
    workspace_status VARCHAR(50) NULL COMMENT '워크스페이스 상태 (CREATING, RUNNING, PAUSED, DELETED)',
    last_pod_ip VARCHAR(45) NULL COMMENT '최근 Pod IP 주소',
    resumed_count INT NULL DEFAULT 0 COMMENT '재개 횟수'
);

-- 사용자 쉘 환경 테이블 (WebSocket 터미널 연결 관리)
DROP TABLE IF EXISTS user_shell_environment CASCADE;
CREATE TABLE user_shell_environment (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '쉘 환경 고유 식별자 (UUID)',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    mission_id BIGINT NOT NULL COMMENT '미션 ID',
    mission_attempt_id VARCHAR(36) NOT NULL COMMENT '미션 시도 ID (mission_attempt.id 참조)',
    user_email VARCHAR(255) NOT NULL COMMENT '사용자 이메일',
    user_name VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    status VARCHAR(255) NOT NULL COMMENT '환경 상태 (PENDING, APPROVED, ACTIVE, PAUSED, SUSPENDED)',
    shell_username VARCHAR(50) COMMENT '쉘 사용자명',
    shell_password VARCHAR(255) COMMENT '쉘 비밀번호',
    root_access_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'root 접근 권한',
    pod_name VARCHAR(100) COMMENT 'Kubernetes Pod 이름',
    namespace VARCHAR(50) COMMENT 'Kubernetes Namespace',
    container_name VARCHAR(100) COMMENT '컨테이너 이름',
    last_accessed_at TIMESTAMP COMMENT '마지막 접근 시간',
    access_count BIGINT NOT NULL DEFAULT 0 COMMENT '접근 횟수',
    approval_notes TEXT COMMENT '승인 노트',
    paused_at TIMESTAMP COMMENT '일시정지 시간',
    resumed_at TIMESTAMP COMMENT '재개 시간',
    suspended_at TIMESTAMP COMMENT '중지 시간',
    shell_state_snapshot TEXT COMMENT '쉘 상태 스냅샷',
    filesystem_snapshot_id VARCHAR(100) COMMENT '파일시스템 스냅샷 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '수정 시간'
);

-- 미션 제출 테이블 (AI 평가 시스템 연동)
DROP TABLE IF EXISTS mission_submission CASCADE;
CREATE TABLE mission_submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '제출 기록 고유 식별자',
    mission_id BIGINT NOT NULL COMMENT '제출대상 미션 ID',
    team_id BIGINT COMMENT '제출하는 팀 ID',
    user_id BIGINT NOT NULL COMMENT '제출 대상 사용자 ID',
    submitted_by BIGINT NOT NULL COMMENT '실제 제출자 ID',
    submission_url VARCHAR(500) COMMENT '제출물 URL',
    github_repository_url VARCHAR(500) COMMENT 'GitHub 리포지토리 URL',
    deployment_url VARCHAR(500) COMMENT '배포 URL',
    submission_notes TEXT COMMENT '제출 시 사용자가 남긴 메모',
    status VARCHAR(255) NOT NULL DEFAULT 'SUBMITTED' COMMENT '제출 상태 (SUBMITTED, REVIEWED, APPROVED, REJECTED)',
    score INT COMMENT '획득 점수',
    max_score INT COMMENT '최대 점수',
    feedback TEXT COMMENT 'AI의 피드백',
    reviewed_by BIGINT COMMENT '리뷰어 ID',
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '제출 시간',
    reviewed_at TIMESTAMP COMMENT '리뷰 완료 시간'
);

-- 명령어 실행 로그 테이블 (AI 평가를 위한 쉘 명령어 기록)
DROP TABLE IF EXISTS command_logs CASCADE;
CREATE TABLE command_logs (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '명령어 로그 고유 식별자 (UUID)',
    attempt_id VARCHAR(36) NOT NULL COMMENT '미션 시도 ID (mission_attempt.id 참조)',
    command TEXT NOT NULL COMMENT '실행된 명령어',
    output LONGTEXT COMMENT '명령어 실행 결과',
    exit_code INT COMMENT '명령어 종료 코드',
    working_directory VARCHAR(500) COMMENT '명령어 실행 디렉토리',
    duration_ms BIGINT COMMENT '명령어 실행 시간 (밀리초)',
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '명령어 실행 시점',
    command_type VARCHAR(50) COMMENT '명령어 분류 (setup, build, deploy, test, git 등)',
    is_significant BOOLEAN NOT NULL DEFAULT FALSE COMMENT '중요 명령어 여부 (체크포인트 생성 대상)',
    step_number INT COMMENT '미션 단계 번호',
    user_id BIGINT COMMENT '사용자 ID',
    environment_id VARCHAR(36) COMMENT '환경 ID',
    context_data LONGTEXT COMMENT '추가 컨텍스트 정보 (JSON)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '로그 생성 시간'
);

-- 미션 저장/체크포인트 테이블 (세션 관리 및 S3 스토리지)
DROP TABLE IF EXISTS mission_save CASCADE;
CREATE TABLE mission_save (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '저장 기록 고유 식별자 (UUID)',
    mission_attempt_id VARCHAR(36) COMMENT '미션 시도 참조 (기존 호환성)',
    attempt_id VARCHAR(36) NOT NULL COMMENT '미션 시도 ID (직접 참조)',
    saved_data TEXT NOT NULL COMMENT '저장된 데이터 (S3 경로)',
    save_note VARCHAR(500) COMMENT '저장 시 사용자가 남긴 메모',
    save_sequence INT NOT NULL DEFAULT 1 COMMENT '저장 순서 (동일 시도 내에서의 순서)',
    save_level VARCHAR(20) NOT NULL DEFAULT 'STANDARD' COMMENT '저장 수준 (LIGHTWEIGHT, STANDARD, COMPREHENSIVE)',
    save_trigger VARCHAR(30) NOT NULL DEFAULT 'MANUAL' COMMENT '저장 트리거 (MANUAL, AUTO_TIMER, COMMAND_CHECKPOINT, PAUSE)',
    current_step VARCHAR(100) COMMENT '현재 진행 단계',
    progress_percent INT DEFAULT 0 COMMENT '진행률 (%)',
    step_score INT DEFAULT 0 COMMENT '현재 단계 점수',
    data_size_bytes BIGINT DEFAULT 0 COMMENT '저장된 데이터 크기 (바이트)',
    compression_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '압축 사용 여부',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '저장 시간'
);

-- ============================================================================
-- 인덱스 생성 (성능 최적화)
-- ============================================================================

-- command_logs 테이블 인덱스
CREATE INDEX idx_attempt_timestamp ON command_logs (attempt_id, executed_at);
CREATE INDEX idx_command_type ON command_logs (command_type);
CREATE INDEX idx_significant_commands ON command_logs (attempt_id, is_significant, executed_at);
CREATE INDEX idx_user_commands ON command_logs (user_id, executed_at);

-- mission_save 테이블 인덱스
CREATE INDEX idx_attempt_sequence ON mission_save (attempt_id, save_sequence DESC);
CREATE INDEX idx_save_trigger ON mission_save (save_trigger);
CREATE INDEX idx_created_at ON mission_save (created_at DESC);

-- mission_attempt 테이블 인덱스
CREATE INDEX idx_user_status ON mission_attempt (user_id, status);
CREATE INDEX idx_mission_started ON mission_attempt (mission_id, started_at);
CREATE INDEX idx_workspace_status ON mission_attempt (workspace_status);

-- user_shell_environment 테이블 인덱스
CREATE INDEX idx_user_status_shell ON user_shell_environment (user_id, status);
CREATE INDEX idx_pod_name ON user_shell_environment (pod_name);

-- mission_submission 테이블 인덱스
CREATE INDEX idx_user_submission ON mission_submission (user_id, submitted_at);
CREATE INDEX idx_mission_submission ON mission_submission (mission_id, submitted_at);

-- ============================================================================
-- AI Evaluation Service 관련 테이블 (참고용)
-- ============================================================================

-- AI 평가 테이블
DROP TABLE IF EXISTS ai_evaluation CASCADE;
CREATE TABLE ai_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'AI 평가 고유 식별자',
    mission_attempt_id VARCHAR(36) NOT NULL UNIQUE COMMENT '미션 시도 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    mission_id VARCHAR(36) NOT NULL COMMENT '미션 ID',
    evaluation_status VARCHAR(20) NOT NULL COMMENT '평가 상태 (PENDING, PROCESSING, COMPLETED, FAILED)',
    evaluation_result JSON COMMENT '평가 결과 (JSON)',
    ai_model_version VARCHAR(50) COMMENT 'AI 모델 버전',
    error_message TEXT COMMENT '에러 메시지',
    mission_title VARCHAR(200) COMMENT '미션 제목',
    mission_type VARCHAR(50) COMMENT '미션 유형',
    submitted_code TEXT COMMENT '제출된 코드',
    s3_log_path VARCHAR(500) COMMENT 'S3 로그 경로',
    s3_workspace_path VARCHAR(500) COMMENT 'S3 워크스페이스 경로',
    evaluation_trigger VARCHAR(30) DEFAULT 'MISSION_COMPLETION' COMMENT '평가 트리거',
    processing_node VARCHAR(100) COMMENT '처리 노드',
    processing_time_ms BIGINT COMMENT '처리 시간 (밀리초)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간'
);

-- AI 평가 요약 테이블
DROP TABLE IF EXISTS evaluation_summary CASCADE;
CREATE TABLE evaluation_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '평가 요약 고유 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    mission_id VARCHAR(36) NOT NULL COMMENT '미션 ID',
    mission_attempt_id VARCHAR(36) NOT NULL UNIQUE COMMENT '미션 시도 ID',
    mission_title VARCHAR(200) COMMENT '미션 제목',
    mission_type VARCHAR(50) COMMENT '미션 유형',
    mission_difficulty VARCHAR(20) COMMENT '미션 난이도',
    overall_score INT COMMENT '전체 점수',
    correctness_score INT COMMENT '정확성 점수',
    efficiency_score INT COMMENT '효율성 점수',
    quality_score INT COMMENT '품질 점수',
    code_quality_score INT COMMENT '코드 품질 점수 (호환성)',
    security_score INT COMMENT '보안 점수 (호환성)',
    style_score INT COMMENT '스타일 점수 (호환성)',
    evaluation_status VARCHAR(20) NOT NULL COMMENT '평가 상태',
    feedback_summary TEXT COMMENT '피드백 요약',
    feedback_details JSON COMMENT '피드백 상세 (JSON)',
    commands_executed INT DEFAULT 0 COMMENT '실행된 명령어 수',
    significant_commands INT DEFAULT 0 COMMENT '중요 명령어 수',
    error_commands INT DEFAULT 0 COMMENT '에러 명령어 수',
    total_execution_time_ms BIGINT DEFAULT 0 COMMENT '총 실행 시간 (밀리초)',
    evaluation_duration_ms BIGINT COMMENT '평가 소요 시간 (밀리초)',
    stamps_earned INT DEFAULT 0 COMMENT '획득한 스탬프 수',
    points_awarded INT DEFAULT 0 COMMENT '지급된 포인트',
    ai_evaluation_id BIGINT COMMENT 'AI 평가 ID 참조',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간'
);

-- 미션 S3 저장소 테이블
DROP TABLE IF EXISTS mission_s3_storage CASCADE;
CREATE TABLE mission_s3_storage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'S3 저장소 고유 식별자',
    mission_attempt_id VARCHAR(36) NOT NULL UNIQUE COMMENT '미션 시도 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    mission_id VARCHAR(36) NOT NULL COMMENT '미션 ID',
    s3_storage_url VARCHAR(500) NOT NULL COMMENT 'S3 저장소 URL',
    command_logs_path VARCHAR(500) COMMENT '명령어 로그 S3 경로',
    workspace_snapshot_path VARCHAR(500) COMMENT '워크스페이스 스냅샷 S3 경로',
    submission_files_path VARCHAR(500) COMMENT '제출 파일 S3 경로',
    bucket_name VARCHAR(255) NOT NULL COMMENT 'S3 버킷명',
    object_key_prefix VARCHAR(500) NOT NULL COMMENT 'S3 객체 키 프리픽스',
    total_size_bytes BIGINT DEFAULT 0 COMMENT '총 크기 (바이트)',
    file_count INT DEFAULT 0 COMMENT '파일 개수',
    last_sync_at TIMESTAMP COMMENT '마지막 동기화 시간',
    access_level VARCHAR(20) DEFAULT 'PRIVATE' COMMENT '접근 레벨 (PRIVATE, TEAM, PUBLIC)',
    expiry_date TIMESTAMP COMMENT '만료 날짜',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간'
);

-- ============================================================================
-- AI Evaluation Service 인덱스
-- ============================================================================

-- ai_evaluation 인덱스
CREATE INDEX idx_ai_user_mission ON ai_evaluation (user_id, mission_id);
CREATE INDEX idx_ai_status ON ai_evaluation (evaluation_status);
CREATE INDEX idx_ai_created_at ON ai_evaluation (created_at);

-- evaluation_summary 인덱스
CREATE INDEX idx_summary_user_mission ON evaluation_summary (user_id, mission_id);
CREATE INDEX idx_summary_score ON evaluation_summary (overall_score);
CREATE INDEX idx_summary_difficulty ON evaluation_summary (mission_difficulty);

-- mission_s3_storage 인덱스
CREATE INDEX idx_s3_user_id ON mission_s3_storage (user_id);
CREATE INDEX idx_s3_mission_id ON mission_s3_storage (mission_id);
CREATE INDEX idx_s3_access_level ON mission_s3_storage (access_level);