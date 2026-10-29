import unittest
from datetime import date, datetime, timezone

from generate_hiltronix_revision import BRANCH, ZONE, daily_revision


def run(run_id, year, month, day, hour, attempt=1, branch=BRANCH):
    return {
        "id": run_id,
        "head_branch": branch,
        "created_at": datetime(year, month, day, hour, tzinfo=timezone.utc).isoformat().replace("+00:00", "Z"),
        "run_attempt": attempt,
    }


class DailyRevisionTests(unittest.TestCase):
    def test_first_build_on_day(self):
        runs = [run(200, 2026, 10, 29, 15)]
        self.assertEqual(daily_revision(runs, date(2026, 10, 29), 200, 1), 1)

    def test_second_build_on_day(self):
        runs = [run(200, 2026, 10, 29, 15), run(201, 2026, 10, 29, 16)]
        self.assertEqual(daily_revision(runs, date(2026, 10, 29), 201, 1), 2)

    def test_winnipeg_midnight_boundary(self):
        # 2026-10-29 04:00 UTC is still October 28 in Winnipeg.
        runs = [run(200, 2026, 10, 29, 4), run(201, 2026, 10, 29, 15)]
        self.assertEqual(daily_revision(runs, date(2026, 10, 29), 201, 1), 1)

    def test_reruns_increment_build_counter(self):
        runs = [run(200, 2026, 10, 29, 15, attempt=2), run(201, 2026, 10, 29, 16)]
        self.assertEqual(daily_revision(runs, date(2026, 10, 29), 201, 1), 3)

    def test_other_branch_does_not_increment(self):
        runs = [run(200, 2026, 10, 29, 15, branch="main"), run(201, 2026, 10, 29, 16)]
        self.assertEqual(daily_revision(runs, date(2026, 10, 29), 201, 1), 1)

    def test_missing_current_run_is_included(self):
        self.assertEqual(daily_revision([], date(2026, 10, 29), 200, 1), 1)


if __name__ == "__main__":
    unittest.main()
