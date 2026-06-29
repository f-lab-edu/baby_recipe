#!/bin/bash
STUDY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$STUDY_DIR/../.." && pwd)"
QUESTIONS_FILE="$STUDY_DIR/questions.json"
DOC_DIR="$PROJECT_DIR/doc"
TODAY=$(date +%Y-%m-%d)
REMIND_FLAG="$STUDY_DIR/.doc_reminded_$TODAY"

# 오늘의 학습 질문
if [ -f "$QUESTIONS_FILE" ]; then
    python3 - <<EOF
import json, datetime

with open("$QUESTIONS_FILE") as f:
    questions = json.load(f)["questions"]

total = len(questions)
day = datetime.datetime.now().timetuple().tm_yday
idx = (day - 1) % total
q = questions[idx]

print()
print("─" * 54)
print(f"📚 BabyRecipe 오늘의 질문 ({idx + 1}/{total})  [{q['category']}]")
print("─" * 54)
print(f"Q. {q['question']}")
print()
print(f"힌트: {q['hint']}")
print(f"파일: src/main/java/com/babyrecipe/{q['file']}")
print("─" * 54)
print("💡 대화형 퀴즈: /loop 실행 후 '퀴즈 시작' 입력")
EOF
fi

# 오늘 doc 파일이 없으면 하루에 한 번 종료 알림 표시
if [ ! -f "$REMIND_FLAG" ] && ! ls "$DOC_DIR/$TODAY"*.md 2>/dev/null | grep -q .; then
    echo ""
    echo "─" "───────────────────────────────────────────────────"
    echo "⚠️  오늘($TODAY) 대화 기록이 아직 없습니다."
    echo "   세션을 마칠 때 '대화 종료'라고 입력하면 자동 저장됩니다."
    echo "──────────────────────────────────────────────────────"
    touch "$REMIND_FLAG"
fi
