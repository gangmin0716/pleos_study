#!/usr/bin/env bash
# O2 이슈 라벨(type · priority · size)을 현재 저장소에 만든다. 이미 있으면 색·설명만 맞춘다(--force).
# 사용: 저장소 폴더에서  bash labels.sh   (gh 로그인 필요)
set -euo pipefail

label() { gh label create "$1" --color "$2" --description "$3" --force >/dev/null && echo "  $1"; }

echo "type"
label "type: feature"        1D76DB "새 기능"
label "type: bug"            D73A4A "버그"
label "type: refactor"       C5DEF5 "동작은 같고 구조만 개선"
label "type: docs"           0075CA "문서"
label "type: chore"          BFD4F2 "빌드·설정·의존성"
label "type: enhancement"    A2EEEF "기존 기능 개선"
label "type: test"           FBCA04 "테스트"
label "type: hotfix"         B60205 "긴급 수정"
label "type: init-project"   5319E7 "초기 설정: 프로젝트"
label "type: init-structure" 5319E7 "초기 설정: 구조"
label "type: init-docs"      5319E7 "초기 설정: 문서"

echo "priority"
label "priority: critical" B60205 "즉시"
label "priority: high"     D93F0B "높음"
label "priority: medium"   FBCA04 "보통"
label "priority: low"      0E8A16 "낮음"

echo "size"
label "size: XS" C2E0C6 "4시간 이내"
label "size: S"  C2E0C6 "1~2일"
label "size: M"  FEF2C0 "3~5일"
label "size: L"  F9D0C4 "1~2주"
label "size: XL" E99695 "2주 이상"
