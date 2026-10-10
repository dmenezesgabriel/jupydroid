#!/usr/bin/env bash
# What this repository's test suites actually hold, counted off the tree.
#
# **This exists because the number was stored and went stale at least eight times.** A retired
# planning document carried a running ledger of recounts, each one noting that the previous figure
# was behind the tree; ADR-0001 records why a number that drifts is produced rather than written
# down.
#
# Three of these are counted differently and the difference is the point:
#   - unit tests are reported as EXECUTIONS by Gradle, because a port contract is written once and
#     run per implementation. A `@Test` grep therefore reads lower, and the gap is the contracts.
#   - instrumented and script tests are @Test / def test SITES.
#   - a driver may declare itself NOT A SUITE FLOW, so the sweep's share is drivers minus tools -
#     and only a `^#`-anchored declaration counts, never a bare grep, which reads too many.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "unit @Test sites          $(grep -rho '@Test' app/src/test --include='*.kt' | wc -l)"
echo "unit test classes         $(grep -rl '@Test' app/src/test --include='*.kt' | wc -l)"
echo "instrumented @Test sites  $(grep -rho '@Test' app/src/androidTest --include='*.kt' | wc -l)"
echo "script def test sites     $(grep -rho 'def test' scripts --include='*_test.py' | wc -l)"
echo "maestro flows             $(find .maestro -maxdepth 1 -name '*.yaml' | wc -l)"
echo "maestro drivers           $(find scripts -maxdepth 1 -name '*-driver.sh' | wc -l)"
echo "  of which are tools      $(grep -l '^# *NOT A SUITE FLOW' scripts/*-driver.sh 2>/dev/null | wc -l)"
echo
echo "unit EXECUTIONS are Gradle's, and higher than the @Test count by the port contracts:"
echo "  ./gradlew :app:testDebugUnitTest   # then read the HTML report's total"
echo "script sites confirmed by:"
echo "  python3 -m unittest discover -s scripts -p '*_test.py'"
