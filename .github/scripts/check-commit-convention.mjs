#!/usr/bin/env node
// O2 커밋 규칙 검사(CI용). 로컬 Claude hook(.claude/hooks/lib/check-commit-msg.mjs)과 같은 규칙이다.
//   node check-commit-convention.mjs "feat: 로그인 화면 구현"     → 한 줄 검사
//   git log --format=%s A..B | node check-commit-convention.mjs   → 여러 줄(stdin) 검사
// 하나라도 틀리면 exit 1. GitHub이 자동으로 만든 "Merge branch …"/"Revert …"는 경고만 한다.

export const TYPES = ["feat", "fix", "docs", "style", "refactor", "chore", "perf", "ci", "revert", "merge", "hotfix"];

/** 첫 줄이 규칙에 맞으면 null, 아니면 사유 */
export const checkLine = (line) => {
  const m = line.match(/^([a-z]+): (.+)$/);
  if (!m) return "`type: subject` 형식이 아닙니다";
  const [, type, subject] = m;
  if (!TYPES.includes(type)) return `허용되지 않은 type "${type}" (허용: ${TYPES.join(", ")})`;
  if ([...subject].length > 50) return `subject가 ${[...subject].length}자입니다(50자 이내)`;
  if (subject.includes(",")) return "쉼표를 쓰지 않습니다";
  if (/\.$/.test(subject)) return "마침표로 끝내지 않습니다";
  if (/(습니다|했다|한다|었다|였다)$/.test(subject)) return "문장식이 아니라 개조식으로 씁니다 (예: ~구현, ~수정)";
  return null;
};

const isGitHubGenerated = (line) => /^(Merge (branch|pull request|remote-tracking)|Revert ")/.test(line);

const main = async () => {
  let lines = process.argv.slice(2);
  if (lines.length === 0) {
    let data = "";
    for await (const chunk of process.stdin) data += chunk;
    lines = data.split("\n");
  }
  let failed = 0;
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    if (isGitHubGenerated(line)) {
      console.log(`::warning::"${line}" — GitHub 자동 메시지입니다. 머지 커밋은 "merge: <브랜치명>"으로 바꿔 주세요.`);
      continue;
    }
    const why = checkLine(line);
    if (why) {
      failed += 1;
      console.log(`::error::"${line}" — ${why}`);
    } else {
      console.log(`ok  ${line}`);
    }
  }
  if (failed) {
    console.log(`\n${failed}건이 커밋 규칙에 맞지 않습니다. 형식: type: subject (50자 이내·개조식·쉼표/마침표 없음)`);
    process.exit(1);
  }
};

if (import.meta.url === `file://${process.argv[1]}`) await main();
