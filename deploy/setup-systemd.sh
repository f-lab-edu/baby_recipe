#!/bin/bash
# EC2에서 실행되는 systemd 서비스 셋업 스크립트 (멱등 — 매 배포마다 실행해도 안전)
set -e

# 환경변수 파일: ~/.bashrc의 앱 관련 export만 systemd EnvironmentFile 형식으로 변환
# (PATH 등 $변수 참조가 있는 시스템 export는 systemd에서 확장되지 않으므로 제외)
# 이후 새 환경변수는 /home/ec2-user/babyrecipe.env에 직접 추가할 것
if [ ! -f /home/ec2-user/babyrecipe.env ]; then
  grep -E '^export (DB_|JWT_|ANTHROPIC_|UPLOAD_|BASE_)' /home/ec2-user/.bashrc | sed 's/^export //' > /home/ec2-user/babyrecipe.env
  chmod 600 /home/ec2-user/babyrecipe.env
fi

# 유닛 파일 설치 (변경 시에만 daemon-reload)
if ! sudo cmp -s /home/ec2-user/babyrecipe.service /etc/systemd/system/babyrecipe.service; then
  sudo cp /home/ec2-user/babyrecipe.service /etc/systemd/system/babyrecipe.service
  sudo systemctl daemon-reload
fi
sudo systemctl enable babyrecipe

# 기존 nohup 방식으로 떠 있는 프로세스가 있으면 종료 (최초 전환 시 1회)
if ! systemctl is-active --quiet babyrecipe; then
  pkill -f 'java -jar' 2>/dev/null || true
  sleep 3
fi
