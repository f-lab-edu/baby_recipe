# BabyRecipe AWS 배포 진행 기록

> 작성일: 2026-06-11
> 위치: doc/2026-06-11-aws-deploy-progress.md
> 전체 흐름: 계정 생성 → 안전장치 → EC2 → RDS → 배포

---

## ✅ 완료된 단계 (1~3단계)

### 1단계: AWS 계정 생성
- 루트 계정 생성 완료 (이메일 + 카드 등록 + 휴대폰 인증)
- 리전: **서울 (ap-northeast-2)**

### 2단계: 안전장치
- 루트 계정 MFA 설정 완료 (Google Authenticator)
  - ⚠️ MFA 비밀 키 백업해둘 것 (휴대폰 분실 대비)
- 빌링 알람 설정 완료 (Budgets → 제로 지출 예산, 이메일 알림)

### 3단계: EC2 생성 및 Java 설치
- **인스턴스**: babyrecipe-server
  - AMI: Amazon Linux 2023
  - 유형: t2.micro (프리 티어)
  - 스토리지: 20~30GB gp3
- **퍼블릭 IP**: `<EC2_PUBLIC_IP>`
  - ⚠️ 인스턴스 중지 후 재시작하면 IP가 바뀜! 바뀌면 ssh 명령어의 IP도 변경 필요
- **키 페어**: `babyrecipe-key.pem` → WSL 홈(`~/`)에 보관, `chmod 400` 적용됨
- **보안 그룹 인바운드 규칙**:
  | 포트 | 프로토콜 | 소스 | 용도 |
  |------|---------|------|------|
  | 22 | SSH | 내 IP | 서버 접속 (집 IP 바뀌면 "내 IP" 재선택 필요) |
  | 8080 | TCP | 0.0.0.0/0 | Spring Boot 앱 공개 |
- **설치 완료**: Java 17 (Corretto), git

### 접속 명령어 (재접속 시)
```bash
cd ~
ssh -i babyrecipe-key.pem ec2-user@<EC2_PUBLIC_IP>
```

### SSH 트러블슈팅 메모
- `Permission denied (publickey)` → chmod 400 확인, pem이 /mnt/c 경로에 있으면 WSL 홈으로 복사
- `Connection timed out` → 보안 그룹 22번 소스 "내 IP" 재선택 (집 IP 변경됨)
- 비상시: EC2 콘솔 → 연결 → EC2 Instance Connect (브라우저 터미널, pem 불필요)

---

## 📋 남은 단계 (4~5단계)

### 4단계: RDS (MySQL) 생성

1. 콘솔 검색창에 "RDS" → "데이터베이스 생성" 클릭
2. 설정:
   - 엔진: **MySQL 8.x**
   - 템플릿: **프리 티어** 선택 (이걸 선택하면 아래 사양이 자동으로 맞춰짐)
   - 인스턴스: db.t3.micro / db.t4g.micro
   - 스토리지: 20GB (자동 조정 끄기 — 과금 방지)
   - DB 인스턴스 식별자: `babyrecipe-db`
   - 마스터 사용자: admin / 비밀번호 직접 설정 (⚠️ 기록해둘 것, 코드에 넣지 말 것)
3. 연결 설정:
   - **퍼블릭 액세스: 아니오** (DB를 인터넷에 직접 노출하지 않음)
   - VPC: 기본값 (EC2와 같은 VPC)
4. 생성 후 (5~10분 소요):
   - RDS 보안 그룹의 인바운드에 **3306 포트, 소스 = EC2의 보안 그룹** 추가
   - 이렇게 하면 "EC2에서만 DB 접근 가능" — 금융권 면접에서 네트워크 격리 설계로 언급 가능한 포인트
5. RDS 상세 화면에서 **엔드포인트** 복사 (예: babyrecipe-db.xxxx.ap-northeast-2.rds.amazonaws.com)
6. 초기 스키마 생성:
   - EC2에서 mysql 클라이언트 설치 후 접속:
   ```bash
   sudo dnf install -y mariadb105   # mysql 클라이언트
   mysql -h <RDS엔드포인트> -u admin -p
   ```
   ```sql
   CREATE DATABASE babyrecipe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

### 5단계: 앱 배포

1. **로컬(WSL)에서 빌드**:
   ```bash
   cd ~/test/baby_recipe
   ./gradlew clean build
   # 결과물: build/libs/*.jar (plain 아닌 쪽)
   ```

2. **jar를 EC2로 전송**:
   ```bash
   scp -i ~/babyrecipe-key.pem build/libs/babyrecipe-0.0.1-SNAPSHOT.jar ec2-user@<EC2_PUBLIC_IP>:~/app.jar
   ```
   (또는 EC2에서 git clone 후 서버에서 빌드해도 됨 — 단 t2.micro는 메모리가 작아 빌드가 느리거나 실패할 수 있어 scp 방식 추천)

3. **DB 접속 정보는 환경변수로** (application.yml에 비밀번호 하드코딩 금지):
   ```yaml
   # application.yml
   spring:
     datasource:
       url: jdbc:mysql://${DB_HOST}:3306/babyrecipe
       username: ${DB_USER}
       password: ${DB_PASSWORD}
   ```

4. **EC2에서 실행**:
   ```bash
   export DB_HOST=<RDS엔드포인트>
   export DB_USER=admin
   export DB_PASSWORD=<비밀번호>
   nohup java -jar app.jar > app.log 2>&1 &
   ```
   - `nohup ... &` : SSH 끊어도 계속 실행
   - 로그 확인: `tail -f app.log`
   - 프로세스 확인: `ps -ef | grep java`
   - 종료: `kill <PID>`

5. **접속 확인**: 브라우저에서 `http://<EC2_PUBLIC_IP>:8080`

### 5단계에서 막히기 쉬운 지점
- 앱이 뜨다가 죽음 → app.log 확인. 대부분 DB 연결 실패 (RDS 보안 그룹 3306 규칙 또는 환경변수 오타)
- t2.micro 메모리 부족(1GB) → 스왑 추가:
  ```bash
  sudo dd if=/dev/zero of=/swapfile bs=128M count=16
  sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  ```
- 브라우저 접속 안 됨 → 보안 그룹 8080 규칙 확인, 앱이 실제로 떠 있는지 `curl localhost:8080`으로 서버 내부에서 먼저 확인

---

## 이후 확장 (선택)
- 도메인 연결 + HTTPS (Route 53 / Let's Encrypt)
- GitHub Actions 배포 자동화 (기존 CI에 CD 추가)
- systemd 서비스 등록 (서버 재부팅 시 앱 자동 시작)
