/**
 * Auth0 Post-Login Action
 * 사용자 로그인 완료 후 백엔드 서버로 이벤트를 전송합니다.
 * 
 * 설치 방법:
 * 1. Auth0 Dashboard > Actions > Library로 이동
 * 2. "Build Custom" 클릭
 * 3. Trigger: "Login / Post Login" 선택  
 * 4. 이 코드를 복사하여 붙여넣기
 * 5. Dependencies에 "axios" 추가 (버전: latest)
 * 6. Secrets에 BACKEND_WEBHOOK_URL 추가
 * 7. Deploy 후 Flow에 추가
 */

const axios = require('axios');

exports.onExecutePostLogin = async (event, api) => {
  const WEBHOOK_URL = event.secrets.BACKEND_WEBHOOK_URL || 'http://localhost:8080/webhook/auth0/user-login';
  
  console.log('Post-Login Action triggered for user:', event.user.email);
  
  try {
    // 백엔드로 전송할 페이로드 구성
    const payload = {
      user: {
        user_id: event.user.user_id,
        email: event.user.email,
        name: event.user.name || event.user.nickname,
        email_verified: event.user.email_verified,
        picture: event.user.picture,
        created_at: event.user.created_at,
        updated_at: event.user.updated_at
      },
      connection: {
        name: event.connection.name,
        strategy: event.connection.strategy
      },
      request: {
        ip: event.request.ip,
        user_agent: event.request.headers['user-agent'],
        query: event.request.query,
        body: event.request.body
      },
      transaction: {
        transaction_id: event.transaction.id,
        protocol: event.transaction.protocol,
        requested_scopes: event.transaction.requested_scopes
      },
      timestamp: new Date().toISOString(),
      event_type: 'user_login'
    };
    
    // 백엔드 웹훅 호출
    const response = await axios.post(WEBHOOK_URL, payload, {
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'Auth0-Action/1.0',
        'X-Auth0-Action': 'post-login',
        'X-Request-ID': event.transaction.id
      },
      timeout: 5000 // 5초 타임아웃
    });
    
    console.log('Successfully notified backend:', response.data);
    
    // 사용자 메타데이터에 로그인 정보 업데이트 (선택사항)
    const lastLoginInfo = {
      last_login_at: new Date().toISOString(),
      last_login_ip: event.request.ip,
      last_login_connection: event.connection.name,
      login_count: (event.user.app_metadata?.login_count || 0) + 1
    };
    
    api.user.setAppMetadata('login_info', lastLoginInfo);
    
    console.log('Updated user metadata for:', event.user.email);
    
  } catch (error) {
    console.error('Failed to notify backend about login:', {
      error: error.message,
      user: event.user.email,
      webhook_url: WEBHOOK_URL,
      stack: error.stack
    });
    
    // 에러가 발생해도 로그인 프로세스는 계속 진행
    // 필요시 api.access.deny() 로 로그인을 차단할 수 있음
  }
};

/**
 * Auth0 Dashboard에서 설정해야 할 Secrets:
 * 
 * BACKEND_WEBHOOK_URL: http://your-backend-domain.com/webhook/auth0/user-login
 * 
 * 로컬 개발 시: http://localhost:8080/webhook/auth0/user-login
 * 운영 환경: https://your-domain.com/webhook/auth0/user-login
 */