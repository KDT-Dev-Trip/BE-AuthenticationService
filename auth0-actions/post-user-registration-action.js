/**
 * Auth0 Post-User Registration Action
 * 신규 사용자 가입 완료 후 백엔드 서버로 이벤트를 전송합니다.
 * 
 * 설치 방법:
 * 1. Auth0 Dashboard > Actions > Library로 이동
 * 2. "Build Custom" 클릭
 * 3. Trigger: "Login / Post User Registration" 선택
 * 4. 이 코드를 복사하여 붙여넣기
 * 5. Dependencies에 "axios" 추가 (버전: latest)
 * 6. Secrets에 BACKEND_WEBHOOK_URL 추가
 * 7. Deploy 후 Flow에 추가
 */

const axios = require('axios');

exports.onExecutePostUserRegistration = async (event, api) => {
  const WEBHOOK_URL = event.secrets.BACKEND_WEBHOOK_URL || 'http://localhost:8080/webhook/auth0/user-signup';
  
  console.log('Post-User Registration Action triggered for user:', event.user.email);
  
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
        family_name: event.user.family_name,
        given_name: event.user.given_name,
        locale: event.user.locale
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
      timestamp: new Date().toISOString(),
      event_type: 'user_signup'
    };
    
    // 백엔드 웹훅 호출
    const response = await axios.post(WEBHOOK_URL, payload, {
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'Auth0-Action/1.0',
        'X-Auth0-Action': 'post-user-registration',
        'X-Request-ID': event.transaction?.id || `signup-${Date.now()}`
      },
      timeout: 5000 // 5초 타임아웃
    });
    
    console.log('Successfully notified backend about signup:', response.data);
    
    // 신규 사용자 메타데이터 설정
    const initialMetadata = {
      signup_at: new Date().toISOString(),
      signup_ip: event.request.ip,
      signup_connection: event.connection.name,
      initial_tickets: 3, // 신규 사용자에게 3개 티켓 제공
      onboarding_completed: false
    };
    
    api.user.setAppMetadata('user_info', initialMetadata);
    
    // 환영 이메일 트리거를 위한 사용자 메타데이터 설정 (선택사항)
    api.user.setUserMetadata('welcome_email_sent', false);
    
    console.log('Set initial metadata for new user:', event.user.email);
    
  } catch (error) {
    console.error('Failed to notify backend about signup:', {
      error: error.message,
      user: event.user.email,
      webhook_url: WEBHOOK_URL,
      stack: error.stack
    });
    
    // 회원가입 실패 시에도 Auth0 가입은 완료됨
    // 필요시 추가 로직 구현 가능
  }
};

/**
 * Auth0 Dashboard에서 설정해야 할 Secrets:
 * 
 * BACKEND_WEBHOOK_URL: http://your-backend-domain.com/webhook/auth0/user-signup
 * 
 * 로컬 개발 시: http://localhost:8080/webhook/auth0/user-signup  
 * 운영 환경: https://your-domain.com/webhook/auth0/user-signup
 */