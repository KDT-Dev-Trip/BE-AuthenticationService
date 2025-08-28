-- DevTrip MSA 데이터베이스 초기화 스크립트
-- 각 서비스별 데이터베이스 생성

-- Authentication Service Database
CREATE DATABASE IF NOT EXISTS `devtrip-authentication` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON `devtrip-authentication`.* TO 'devtrip_user'@'%';

-- Payment Service Database  
CREATE DATABASE IF NOT EXISTS `devtrip-payment` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON `devtrip-payment`.* TO 'devtrip_user'@'%';

-- User Management Service Database
CREATE DATABASE IF NOT EXISTS `devtrip-user-mgmt` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON `devtrip-user-mgmt`.* TO 'devtrip_user'@'%';

-- Mission Management Service Database
CREATE DATABASE IF NOT EXISTS `devtrip-mission-mgmt` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON `devtrip-mission-mgmt`.* TO 'devtrip_user'@'%';

-- AI Evaluation Service Database
CREATE DATABASE IF NOT EXISTS `devtrip-ai-evaluation` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON `devtrip-ai-evaluation`.* TO 'devtrip_user'@'%';

-- Monitoring Service Database (개발 예정)
CREATE DATABASE IF NOT EXISTS `devtrip-monitoring` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON `devtrip-monitoring`.* TO 'devtrip_user'@'%';

FLUSH PRIVILEGES;

-- 각 데이터베이스 생성 확인 로그
SELECT 'DevTrip MSA databases created successfully' AS status;