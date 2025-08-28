# DevTrip 카프카 이벤트 가이드

이 문서는 DevTrip 프로젝트의 각 서비스별 카프카 이벤트를 정리한 문서입니다.

## 1. AI Evaluation Service (평가 서비스)

### Producer Events (발행하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| evaluation-events | evaluation.started | AI 평가 프로세스 시작 | - 다른 서비스에 평가 시작 알림<br>- 사용자에게 평가 진행 상태 알림<br>- 평가 리소스 준비 및 할당 |
| evaluation-events | evaluation.failed | AI 평가 프로세스 실패 | - 실패 원인 분석 및 로깅<br>- 재시도 가능 여부 판단<br>- 자동 재시도 또는 수동 개입 필요 알림<br>- 사용자에게 실패 알림 전송 |
| evaluation-events | evaluation.retry.requested | AI 평가 재시도 요청 | - 재시도 조건 확인<br>- 이전 실패 원인 검토<br>- 재시도 큐에 등록<br>- 재시도 스케줄링 |
| evaluation-events | evaluation.retry.completed | AI 평가 재시도 완료 | - 재시도 결과 검증<br>- 성공 시 정상 흐름으로 복귀<br>- 실패 시 최종 실패 처리<br>- 재시도 이력 업데이트 |
| evaluation.completed | evaluation.completed | AI 평가 완료 | - 평가 결과를 미션 서비스에 전달<br>- 사용자에게 평가 완료 알림<br>- 평가 통계 업데이트<br>- 다음 단계 프로세스 트리거 |

### Consumer Events (구독하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| mission.completed | mission.completed | 미션 완료 이벤트 수신 | - 미션 완료 데이터 검증<br>- AI 평가 시작 준비<br>- S3에서 미션 데이터 로드<br>- 평가 프로세스 시작 |

---

## 2. Authentication Service (인증 서비스)

### Producer Events (발행하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| auth-events | user.registered | 사용자 회원가입 완료 | **⚠️ 현재 미구현** - 추후 구현 예정 |
| auth-events | user.login | 사용자 로그인 | **⚠️ 현재 미구현** - 추후 구현 예정 |
| auth-events | user.logout | 사용자 로그아웃 | **⚠️ 현재 미구현** - 추후 구현 예정 |

### Consumer Events (구독하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| payment-events | payment.subscription-renewal-failed | 구독 갱신 결제 실패 | - 사용자 결제 실패 상태 업데이트<br>- 보안 이메일 발송<br>- 3회 실패시 계정 일시 정지<br>- 보안 로그 기록 |
| payment-events | payment.subscription-changed | 구독 플랜 변경 | - 사용자 역할/권한 업데이트<br>- 업그레이드 시 추가 권한 부여<br>- 다운그레이드 시 권한 제한<br>- 계정 접근 권한 복원 |
| payment-events | payment.ticket-balance-low | 티켓 잔량 부족 경고 | - 세션에 잔액 부족 플래그 설정<br>- 잔액 부족 마케팅 이메일<br>- 기본 플랜 사용자 업그레이드 캠페인<br>- 보안 로그 기록 |
| mission-events | mission.paused | 미션 일시정지 | - 미션 세션 상태 보안 업데이트<br>- 장시간 일시정지시 보안 알림<br>- 세션 타임아웃 조정<br>- 의심스러운 패턴 감지 |
| mission-events | mission.resumed | 미션 재개 | - 미션 세션 상태 복원<br>- 긴 일시정지 후 추가 보안 검증<br>- 세션 보안 레벨 복원<br>- 보안 감사 로그 |
| mission-events | mission.resource-provisioning-failed | 리소스 프로비저닝 실패 | - 보안 이벤트 로깅<br>- 연속 실패시 임시 계정 제한<br>- 시스템 관리자 알림<br>- 리소스 공격 패턴 감지 |
| mission-events | mission.resource-cleanup-completed | 리소스 정리 완료 | - 미션 관련 임시 권한 제거<br>- 세션 플래그 정리<br>- 완료시 인증서 발행 준비<br>- 정리 완료 보안 확인 |
| evaluation-events | evaluation.started | 평가 시작 | - 사용자 최근 활동 업데이트<br>- 평가 이력 기록<br>- 평가 시작 통계 업데이트 |
| evaluation-events | evaluation.failed | 평가 실패 | - 실패 이력 기록<br>- 실패 패턴 분석 데이터 수집<br>- 반복 실패시 기술지원 플래그<br>- 실패 횟수 증가 |
| evaluation-events | evaluation.retry-requested | 평가 재시도 요청 | - 재시도 이력 기록<br>- 재시도 횟수 업데이트<br>- 재시도 패턴 분석 |
| evaluation-events | evaluation.retry-completed | 평가 재시도 완료 | - 재시도 결과 기록<br>- 성공시 성과 업데이트<br>- 실패시 수동 검토 플래그<br>- 재시도 완료 통계 업데이트 |

---

## 3. Mission Management Service (미션 관리 서비스)

### Producer Events (발행하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| mission-events | mission.started | 미션 시작 | - 미션 환경 구성<br>- 리소스 할당<br>- 웹소켓 연결 설정<br>- 모니터링 시작 |
| mission-events | mission.paused | 미션 일시정지 | - 현재 진행상황 저장<br>- 리소스 일시 해제<br>- 세션 보존<br>- 재개 가능 시간 설정 |
| mission-events | mission.resumed | 미션 재개 | - 저장된 진행상황 복원<br>- 리소스 재할당<br>- 웹소켓 재연결<br>- 모니터링 재시작 |
| mission.completed | mission.completed | 미션 완료 | - AI 평가 서비스에 평가 요청<br>- 미션 환경 정리<br>- 결과 데이터 S3 저장<br>- 사용자 진행상황 업데이트 |
| resource-events | resource.provisioning.failed | 리소스 프로비저닝 실패 | - 실패 원인 분석<br>- 대체 리소스 시도<br>- 사용자에게 오류 알림<br>- 자동 복구 시도 |
| resource-events | resource.cleanup.completed | 리소스 정리 완료 | - 리소스 사용량 업데이트<br>- 비용 계산<br>- 다음 미션 준비<br>- 정리 로그 기록 |

### Consumer Events (구독하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| user-events | user.registered | 사용자 등록 이벤트 | - 사용자별 미션 진행상황 초기화<br>- 기본 미션 접근 권한 설정<br>- 사용자 데이터 디렉토리 생성<br>- 초기 가이드 미션 할당 |
| evaluation.completed | evaluation.completed | AI 평가 완료 이벤트 | - 평가 결과를 미션 기록에 저장<br>- 사용자에게 결과 알림<br>- 다음 미션 추천<br>- 성취도 업데이트 |
| resource-events | resource.created | 리소스 생성 완료 | - 미션 환경 활성화<br>- 사용자에게 준비 완료 알림<br>- 웹소켓 연결 활성화<br>- 미션 시작 가능 상태로 변경 |

---

## 4. Payment Service (결제 서비스)

### Producer Events (발행하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| payment-events | payment.subscription-renewal-failed | 구독 갱신 결제 실패 | - 인증/사용자 서비스에 실패 알림<br>- 재시도 스케줄링<br>- 사용자에게 결제 실패 알림<br>- 3회 실패시 서비스 제한 |
| payment-events | payment.subscription-changed | 구독 플랜 변경 | - payment-events와 subscription-events 토픽 동시 발행<br>- 인증/사용자 서비스에 권한 변경 알림<br>- 업그레이드/다운그레이드 처리<br>- 환불 처리 (해당시) |
| payment-events | payment.ticket-balance-low | 티켓 잔량 부족 | - 잔액 부족 알림<br>- 추천 충전량 제안<br>- 기본 플랜 사용자 업그레이드 유도<br>- 자동 충전 안내 |
| subscription-events | payment.subscription-changed | 구독 변경 (호환성) | - 기존 토픽 호환성 유지<br>- 사용자 관리 서비스 연동<br>- 플랜 변경 알림 |

### Consumer Events (구독하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| user-events | user.registered | 사용자 등록 이벤트 | **⚠️ 현재 미구현** - 추후 구현 예정 |
| auth-events | user.login | 사용자 로그인 이벤트 | **⚠️ 현재 미구현** - 추후 구현 예정 |
| mission-events | mission.started | 미션 시작 이벤트 | - 티켓 사용량 차감<br>- 사용량 통계 업데이트<br>- 잔량 부족 시 알림 트리거 |
| mission-events | mission.completed | 미션 완료 이벤트 | **⚠️ 현재 미구현** - 추후 구현 예정 |
| evaluation-events | evaluation.completed | 평가 완료 이벤트 | **⚠️ 현재 미구현** - 추후 구현 예정 |

---

## 5. User Management Service (사용자 관리 서비스)

### Producer Events (발행하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| user-events | user.profile.updated | 사용자 프로필 업데이트 | - 다른 서비스에 프로필 변경 동기화<br>- 구독 플랜 정보 포함<br>- 변경 이력 기록<br>- 관련 권한 재검토 |
| user-events | user.settings.changed | 사용자 설정 변경 | - 개인화 설정 적용<br>- 잔액 부족/구독 상태 플래그<br>- 알림 설정 업데이트<br>- 서비스 제한 상태 동기화 |

### Consumer Events (구독하는 이벤트)

| 이벤트 토픽 | 이벤트 명 | 이벤트 설명 | 이벤트 발생 이후 로직 |
|------------|----------|------------|---------------------|
| auth-events | user.registered | 사용자 등록 이벤트 | - 사용자 프로필 초기화<br>- 기본 설정 적용<br>- 시스템 권한 할당<br>- 온보딩 프로세스 시작 |
| payment-events | payment.subscription-renewal-failed | 구독 갱신 실패 | - 사용자 결제 실패 상태 기록<br>- 결제 실패 알림 전송<br>- 3회 실패시 서비스 제한 알림<br>- 설정 변경 이벤트 발행 |
| payment-events | payment.subscription-changed | 구독 플랜 변경 | - 사용자 구독 플랜 캐시 업데이트<br>- 플랜 변경 축하/안내 알림<br>- 업그레이드시 환영 가이드 제공<br>- 프로필 업데이트 이벤트 발행 |
| payment-events | payment.ticket-balance-low | 티켓 잔량 부족 | - 잔액 부족 알림 전송<br>- 기본 플랜시 업그레이드 권유<br>- 자동 충전 안내<br>- 설정 변경 이벤트 발행 |
| subscription-events | subscription.changed | 구독 변경 (기존 호환) | - 플랜 변경 적용<br>- 권한 재설정<br>- 요금제 안내<br>- 변경사항 알림 |
| mission-events | mission.completed | 미션 완료 이벤트 | **⚠️ 현재 미구현** - 추후 구현 예정 |
| evaluation-events | evaluation.completed | 평가 완료 이벤트 | **⚠️ 현재 미구현** - 추후 구현 예정 |

---

## 카프카 토픽 목록

### 주요 토픽 정보
- **auth-events**: 인증 관련 이벤트 (로그인, 회원가입, 로그아웃)
- **user-events**: 사용자 관리 이벤트 (프로필, 설정, 팀, 성취)
- **mission-events**: 미션 관련 이벤트 (시작, 일시정지, 재개, 완료)
- **mission.completed**: 미션 완료 전용 토픽 (AI 평가 트리거)
- **evaluation-events**: 평가 관련 이벤트 (시작, 실패, 재시도, 완료)
- **evaluation.completed**: 평가 완료 전용 토픽 (평가 결과 전달)
- **payment-events**: 결제 관련 이벤트 (구독, 결제, 티켓)
- **subscription-events**: 구독 변경 전용 토픽
- **resource-events**: 인프라 리소스 이벤트 (생성, 실패, 정리)

### 컨슈머 그룹
- **ai-evaluation-group**: AI 평가 서비스 그룹
- **auth-service-*-group**: 인증 서비스 그룹들
- **user-service-*-group**: 사용자 관리 서비스 그룹들
- **mission-service-*-group**: 미션 관리 서비스 그룹들
- **mission-management-group**: 미션 관리 통합 그룹
- **payment-service-*-group**: 결제 서비스 그룹들

---

## ⚠️ 현재 구현 상태

### 구현 완료된 이벤트 핸들러
- **인증 서비스**: payment-events, mission-events, evaluation-events 구독 처리
- **사용자 관리 서비스**: payment-events, subscription-events, auth-events 구독 처리  
- **결제 서비스**: 3개 이벤트 발행 기능 (subscription-renewal-failed, subscription-changed, ticket-balance-low)
- **AI 평가 서비스**: evaluation 관련 이벤트 발행 및 mission.completed 구독
- **미션 관리 서비스**: mission 관련 이벤트 발행 및 기타 이벤트 구독

### 미구현 이벤트 핸들러 (⚠️ 표시)
- 인증 서비스의 이벤트 발행 기능 (user.registered, user.login, user.logout)
- 결제 서비스의 일부 컨슈머 (user-events, auth-events, mission/evaluation 완료 이벤트)
- 사용자 관리 서비스의 일부 컨슈머 (mission/evaluation 완료 이벤트)
- 일부 서비스 간 이벤트 연동

---

## 테스트 가이드

### 1. 이벤트 발행 테스트
각 서비스의 이벤트 발행 기능을 테스트할 때는 다음을 확인하세요:
- 이벤트 스키마 정합성 (실제 DTO 클래스 기준)
- 필수 필드 포함 여부
- 타임스탬프 정확성
- 이벤트 ID 유니크성

### 2. 이벤트 수신 테스트
이벤트 수신 기능을 테스트할 때는 다음을 확인하세요:
- 올바른 토픽 구독 여부
- 컨슈머 그룹 설정 정확성
- 에러 핸들링 및 로깅
- 실제 비즈니스 로직 처리 여부

### 3. 실제 구현된 통합 테스트 시나리오
- **결제 실패 흐름**: 결제 서비스 → 인증/사용자 서비스 → 알림 발송
- **구독 변경 흐름**: 결제 서비스 → 인증/사용자 서비스 → 권한 업데이트  
- **미션 완료 흐름**: 미션 서비스 → AI 평가 서비스 → 평가 시작
- **평가 이벤트 흐름**: AI 평가 서비스 → 인증 서비스 → 통계 업데이트

### 4. 테스트시 주의사항
- **미구현 핸들러**: ⚠️ 표시된 이벤트는 실제로 처리되지 않음
- **로깅 수준**: 대부분의 핸들러는 로깅 위주로 구현됨
- **의존성 서비스**: 일부 핸들러는 아직 구현되지 않은 서비스 메서드 호출

### 5. 모니터링 포인트
- 이벤트 처리 지연시간
- 실패한 이벤트 수 (에러 로그 확인)
- 컨슈머 랙(Lag)
- 토픽별 메시지 처리량