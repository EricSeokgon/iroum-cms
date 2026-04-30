# iroum-cms 운영 배포 매뉴얼

## 아키텍처 개요

```
인터넷
  │
  ▼
nginx:80 (iroum-nginx)
  ├─ admin.iroum.co.kr  →  admin-fe:80  (관리자 SPA)
  ├─ www.iroum.co.kr    →  public-fe:80 (공공 SPA)
  └─ /api/              →  backend:8080 (Spring Boot)
                                  │
                            postgres:5432
                            redis:6379
```

## 사전 요건

| 항목 | 버전 |
|------|------|
| Docker Engine | 24.0+ |
| Docker Compose Plugin | 2.20+ |
| 운영 서버 RAM | 최소 4GB |
| 디스크 | 최소 20GB |

## 최초 배포 절차

### 1. 환경 변수 파일 생성

```bash
cp deploy/.env.example deploy/.env
```

`deploy/.env` 에서 아래 항목을 반드시 교체한다:

| 변수 | 생성 명령 | 설명 |
|------|-----------|------|
| `DB_PASSWORD` | `openssl rand -base64 32` | PostgreSQL 비밀번호 |
| `JWT_SECRET` | `openssl rand -base64 48` | JWT 서명 키 (256bit+) |
| `REDIS_PASSWORD` | `openssl rand -hex 32` | Redis AUTH 비밀번호 |
| `ACCESS_LOG_IP_SALT` | `openssl rand -base64 32` | IP 익명화 솔트 |
| `AES_KEY` | `openssl rand -base64 32` | AES-256 암호화 키 |

### 2. 이미지 빌드

```bash
# 프로젝트 루트에서 실행
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env build
```

### 3. 서비스 시작

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d
```

### 4. 헬스 확인

```bash
# 전체 서비스 상태 확인
docker compose -f deploy/docker-compose.prod.yml ps

# 백엔드 헬스 직접 확인
curl http://localhost/actuator/health
```

## 일상 운영

### 로그 확인

```bash
# 전체 로그 (실시간)
docker compose -f deploy/docker-compose.prod.yml logs -f

# 백엔드 로그만
docker compose -f deploy/docker-compose.prod.yml logs -f backend

# nginx 접근 로그
docker compose -f deploy/docker-compose.prod.yml logs -f nginx
```

### 서비스 재시작

```bash
# 특정 서비스만 재시작
docker compose -f deploy/docker-compose.prod.yml restart backend

# 전체 재시작
docker compose -f deploy/docker-compose.prod.yml restart
```

### 업데이트 배포 (제로다운타임)

```bash
# 1. 새 이미지 빌드
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env build backend

# 2. 백엔드만 롤링 업데이트
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --no-deps backend

# 3. 헬스 확인
docker compose -f deploy/docker-compose.prod.yml ps backend
```

### 프론트엔드 업데이트

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env build admin-fe public-fe
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --no-deps admin-fe public-fe
```

## 헬스 체크 엔드포인트

| 엔드포인트 | 접근 | 설명 |
|-----------|------|------|
| `GET /actuator/health` | 공개 | Spring Boot 전체 헬스 (DB 연결 포함) |
| `GET /actuator/metrics` | 내부망 | JVM, HTTP 메트릭 |
| `GET /actuator/prometheus` | 내부망 | Prometheus 스크레이프 엔드포인트 |
| `GET /actuator/loggers` | 내부망 | 런타임 로그 레벨 변경 |

> 내부망: CIDR `10.0.0.0/8`, `172.16.0.0/12` (`.env` `ADMIN_IP_WHITELIST` 참조)

## 로그 구조 (운영 — JSON)

운영 프로파일(`SPRING_PROFILES_ACTIVE=prod`)에서 백엔드는 JSON 구조화 로그를 stdout으로 출력한다.

```json
{
  "@timestamp": "2026-04-30T10:00:00.123Z",
  "@version": "1",
  "message": "요청 처리 완료",
  "logger_name": "kr.co.ircp.cms.domain.auth.service.AuthServiceImpl",
  "level": "INFO",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "requestId": "a1b2c3d4-...",
  "clientIp": "10.0.0.1",
  "userId": "42"
}
```

로그 수집: Loki, Elasticsearch, 또는 CloudWatch Logs Agent로 stdout 수집.

## 데이터베이스 백업

```bash
# 수동 덤프
docker exec iroum-postgres pg_dump -U ${DB_USER} iroum_cms | gzip > backup_$(date +%Y%m%d).sql.gz

# 복원
gunzip -c backup_20260430.sql.gz | docker exec -i iroum-postgres psql -U ${DB_USER} iroum_cms
```

## 트러블슈팅

### 백엔드가 healthy 상태가 되지 않는 경우

```bash
# 컨테이너 내부 로그 확인
docker compose -f deploy/docker-compose.prod.yml logs backend --tail=100

# DB 연결 확인
docker exec iroum-postgres pg_isready -U ${DB_USER} -d iroum_cms
```

### nginx 502 Bad Gateway

```bash
# 업스트림 서비스 상태 확인
docker compose -f deploy/docker-compose.prod.yml ps

# nginx 설정 검증
docker exec iroum-nginx nginx -t
```

### 디스크 정리

```bash
# 사용하지 않는 이미지/컨테이너 정리
docker system prune -f

# 볼륨은 제외하고 정리 (데이터 보존)
docker system prune -f --filter "until=24h"
```

## 보안 체크리스트

- [ ] `deploy/.env` Git 미포함 확인 (`.gitignore` 검토)
- [ ] `JWT_SECRET` 256bit 이상 랜덤 키 사용 확인
- [ ] `DB_PASSWORD` 복잡한 비밀번호 사용 확인
- [ ] nginx `ADMIN_IP_WHITELIST` CIDR을 실제 모니터링 서버 IP로 제한
- [ ] `/actuator/prometheus` 외부망 접근 차단 확인 (`curl -I http://your-domain/actuator/prometheus` → 403)
- [ ] HTTPS 인증서 설정 (Let's Encrypt 또는 사설 CA)
- [ ] `Strict-Transport-Security` 헤더 활성화 (nginx conf 주석 해제)
- [ ] 정기 이미지 업데이트 (취약점 패치)
