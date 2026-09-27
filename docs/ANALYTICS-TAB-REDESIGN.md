# Analytics tab — redesign notes

> **Status: Load tab decided and shipped (see §0). The rest of the tab is Option C,
> in progress — questions in §8.**
> This document records what the Analytics tab is wired to today, what the server
> already gives us, and which redesigns are possible *without touching the server*
> (which is a black box — see §6).
>
> Source of the brief: the tab is comprehensible only in "Overview"; the other tabs
> read as "data nonsense"; the "8 achievements" figure is shown without ever saying
> *which* achievements; "Load" is not understood at all.

---

## 0. Decisions taken

1. **Load tab — keep the "Explained" design.** Three candidates were compared in-app on
   real data; the status quo (90 raw rows, *CTL*/*ATL*/*TSB*) and the jargon-free
   variant were both rejected. The switcher and the rejected variants are **deleted**;
   `ui/analytics/LoadTab.kt` is now the tab, unconditionally, with no dev-only
   affordance anywhere.
2. **The tab as a whole — Option C**, with **Load kept as its own section**, precisely
   because in Explained mode it makes sense to a reader who is not a coach.
3. **Achievements** are now listed by name and description on Overview (complaint 1),
   and **form status** uses the server's own plain-English sentence rather than a bare
   enum (complaint 4). Both were dead data on the wire; no server change.

## 1. The brief, in the user's words

1. "I generally understand only the *Overview*, with the exception that the app
   shows me that I have 8 achievements, but does not show me what those
   achievements are."
2. "The rest of the tabs is just *data nonsense* to me."
3. Strava's **Weekly** tab shows a graph comparing this week's activities against
   previous weeks — we have nothing like it.
4. "And the *Load* tab? I totally do not understand what it does, what it is for
   and what its purpose is."

---

## 2. How the tab is wired today

Five tabs (`ui/analytics/AnalyticsTab.kt:33`):

```
Overview | Weekly | Monthly | Yearly | Load
```

`AnalyticsTab.kt:57-63` dispatches straight to a content composable. There is no
shared model, no cross-tab navigation, and no lazy loading — the ViewModel fires
**all** requests on open regardless of which tab is showing
(`AnalyticsViewModel.kt:70-74`):

| Call | Endpoint | Where the data goes |
| --- | --- | --- |
| `dashboard()` | `GET api/web/analytics/dashboard` | Overview only |
| `weeklySummaries(12)` | `GET …/analytics/summaries/weekly?weeks=12` | Overview chart + Weekly tab |
| `monthlySummaries(12)` | `…/summaries/monthly?months=12` | Monthly tab |
| `yearlySummaries(5)` | `…/summaries/yearly?years=5` | Yearly tab |
| `trainingLoad(90)` | `GET …/analytics/training-load?days=90` | Load tab |

**Declared in the Retrofit interface and implemented in the repository, but never
called by any ViewModel** (`AnalyticsRepository.kt`):

| Method | Endpoint | Status |
| --- | --- | --- |
| `achievements()` (:41) | `GET …/analytics/achievements` | **dead code** |
| `personalRecords()` (:29) | `GET …/analytics/personal-records` | **dead code** |
| `formStatus()` (:65) | `GET …/analytics/form-status` | **dead code** |

What each screen actually renders:

- **Overview** (`AnalyticsOverview.kt`) — a 12-week distance bar chart, a "This
  week" card (3 stats), and three `MiniStatCard`s: *Personal records* (count),
  *Achievements* (**count only**), *Form* (a bare enum name). It ignores
  `recentAchievements` and `recentPersonalRecords`, which the dashboard already
  sends.
- **Weekly / Monthly / Yearly** (`AnalyticsSummaries.kt:24-46`) — one and the same
  `SummariesList` for all three: a flat, reversed list of cards, each showing the
  period start as its title and three numbers. No chart, no trend, no comparison
  to the previous period. This is the "data nonsense".
- **Load** (`AnalyticsSummaries.kt:49-78`) — 90 daily cards, each with a
  progress bar and four numbers labelled *Stress*, *Fitness (CTL)*, *Fatigue
  (ATL)*, *Form*. This is where the jargon lives.

---

## 3. What the server already gives us

Everything below is available **today, unmodified** (`analytics/boundary/AnalyticsResource.java`).

### 3.1 Achievements — the whole story is already on the wire

`GET /analytics/achievements` (:153) returns the user's **complete** list,
newest first, **no cap** (`findByUserIdOrderByEarnedAtDesc`). Each item
(`AchievementView`):

```
id, userId, achievementType, name, description,
badgeIcon, badgeColor, earnedAt, activityId, metadata, createdAt
```

So "you have 8 achievements" can become *"your 8 achievements, by name, with the
activity that earned them"* — with **zero server work**.

There are 25 defined `AchievementType`s (`FIRST_ACTIVITY`, `DISTANCE_10K`,
`STREAK_365_DAYS`, `MOUNTAINEER_10000M`, …), but the endpoint only returns the
**earned** ones. See §6 for the "8 of 25" question.

### 3.2 Form status — the server already explains itself

`GET /analytics/form-status` (:186) returns:

```json
{ "formStatus": "FATIGUED",
  "description": "High fatigue detected. Consider taking a rest day." }
```

That `description` is plain English, written server-side (`getFormStatusDescription`).
The app's `FormStatusDto` (`AnalyticsDtos.kt:87-91`) **has no `description` field
at all**, and the method is never called. The "Form" `MiniStatCard` therefore shows
the raw enum (`"Fatigued"`), and the Load tab shows `CTL`/`ATL`/`TSB` with no
explanation. **The fix is to read a field we already throw away.**

### 3.3 Summaries — enough for a real comparison chart

`GET …/summaries/{weekly,monthly,yearly}` return `ActivitySummary` rows with:

```
periodType, periodStart, periodEnd,
activityCount, totalDurationSeconds, totalDistanceMeters, totalElevationGainMeters,
avgSpeedMps, maxSpeedMps,
activityTypeBreakdown (Map<String,Int>),   // {"Run": 5, "Ride": 3}
personalRecordsSet, achievementsEarned
```

Twelve weekly rows is everything needed for a "this week vs the last N weeks"
chart, a week-over-week delta, and a type mix — **computed on the device, no
server change**.

### 3.4 Personal records

`GET /analytics/personal-records` and the dashboard's `recentPersonalRecords`
already return `PersonalRecordView` (activityType, recordType, value, unit,
activityId, achievedAt). Also unused.

### 3.5 Training load

`GET …/analytics/training-load?days=90` returns per-day
`TrainingLoadView` (date, activityCount, distance, elevation, trainingStressScore,
acuteTrainingLoad, chronicTrainingLoad, trainingStressBalance). The raw material
is fine; only the presentation is the problem.

---

## 4. Diagnosis

The tab is not failing for lack of data. It is failing for three reasons:

1. **Data is fetched and thrown away.** `achievements`, `personalRecords` and
   `formStatus` are implemented end-to-end in the repository and never invoked.
   `DashboardDto.recentAchievements` is parsed and never rendered. This is why the
   achievement count appears with no explanation: the explanation was already
   fetched and discarded.
2. **Every period tab is the same flat list.** Weekly, Monthly and Yearly are one
   composable, so none of them can express the question the user actually has
   ("am I doing more or less than last week?"). A list of totals is the raw output
   of a SQL `GROUP BY`; it is not an answer.
3. **The jargon is presented raw.** `CTL`, `ATL`, `TSB` are sports-science
   acronyms with no glossary, and the server's own plain-English `description` is
   discarded. Ninety consecutive rows of that reads as noise.

---

## 5. Design options

Three directions. All are server-independent. They differ mainly in effort and in
how much they change.

### Option A — "Earn it back" (smallest, highest value/effort)

Keep the five tabs, fix only what is actively unhelpful.

- Overview: turn *Achievements* from a number into the **5 most recent
  achievements** (name + description + badge, tapping through to the activity).
  Turn *Personal records* into the 5 most recent PRs.
- Load: keep the tab, but show the server's `description` next to the Form value
  and add a one-line glossary for CTL/ATL/TSB. Collapse the 90 rows to a chart with
  a few detail rows.
- Weekly/Monthly/Yearly: unchanged except for labels.

*Effort: small. Risk: low. Solves complaints 1 and 4 only.*

### Option B — "Trends" (the Strava-like answer)

Replace the Weekly/Monthly/Yearly list-tabs with a single **Trends** view built
around one idea: *this period vs the ones before it*.

- One chart (distance and/or duration) with the current period highlighted and the
  rest as context — device-computed from the 12 rows already fetched.
- A delta line: "+12% vs last week", computed on device.
- A type-mix breakdown from `activityTypeBreakdown`.
- Period selector (week / month / year) instead of three separate tabs.
- The raw period list becomes a secondary "All periods" section, so nothing is
  lost.

*Effort: medium. Risk: medium (charting has to be dependency-free — the project
has no chart library, see §7). Solves complaints 2 and 3.*

### Option C — "What does this mean for me" (opinionated, largest)

Reduce Analytics to three questions a recreational athlete actually asks:

1. **Am I consistent?** (volume + frequency over the last 3 months)
2. **Am I improving?** (distance/time trend, PRs)
3. **What have I achieved?** (achievements, shown properly, with progress)

Training load is demoted from a tab to a small, explained card on Overview — or
dropped from the user-facing surface entirely. It is a coach's tool; for the
audience described in the brief it is the single biggest source of "data
nonsense".

*Effort: large. Risk: medium — it is a judgement call, and some users may want
the raw numbers.*

---

## 6. What needs a server decision (and what does not)

Per the project rule, the instance is a **black box**. The following are the
honest limits of that.

**Possible with no server change (i.e. the large majority of the redesign):**

- Everything in §3.1–§3.5 — achievements, PRs, form-status description,
  comparison charts, deltas, type mix, charts of the 90-day load.
- All comparison maths is on-device: the server sends raw per-period totals and the
  app already has 12 weekly + 12 monthly + 5 yearly rows.

**Genuinely blocked without a server change:**

| Want | Why it is blocked | Options |
| --- | --- | --- |
| "8 of 25 achievements unlocked" | The server exposes 25 `AchievementType`s but no endpoint returns the *catalogue*; only earned rows come back. | (a) show only what is earned — honest, and enough for now; (b) hardcode the 25 types in the app — rejected, it duplicates server truth and will drift; (c) ask the server team to add a catalogue endpoint. |
| "Compare to the same week last year" | Only 12 weekly rows are fetched; the endpoint would accept a larger `weeks`, and 5 yearly rows exist, but a true year-over-year weekly comparison needs either more rows or a new query. | Fetch more weeks (server already supports `?weeks=N`) and compute locally — **possible today**, just not currently requested. |
| Drill-down from a bar to the activities in that week | Summaries are aggregates with no activity ids. | Not possible without a server change. Can be worked around by filtering the existing timeline list by date client-side. |
| Any new metric (e.g. "sessions per month") | Needs new aggregation. | Not possible. |

---

## 7. Constraints to design within

- **No new dependencies.** The project deliberately carries no charting library
  (the existing chart is hand-built Compose: `AnalyticsOverview.kt:84`), and
  keeping the F-Droid audit clean is a stated constraint. Any chart must be
  Compose primitives, as the current one is.
- **Five parallel requests on tab open** is a cost worth revisiting: Monthly and
  Yearly data is fetched even when the user never leaves Overview.
- **Android-free pure logic is the house style** (`NotificationText`,
  `NotificationPolling`, `NtfyMessages` …). Any delta/percentage/series maths
  should live in a plain Kotlin object with JVM unit tests, not inside a
  composable.

---

## 8. Open questions

1. ~~**The "Load" tab.**~~ **DECIDED: keep "Explained"**, as its own section.
2. **"8 of 25"?** Is showing only the earned achievements enough, or is progress
   towards the locked ones wanted (which needs a server catalogue endpoint)?
3. ~~**How far do we go?**~~ **DECIDED: Option C.** The open part is now *which tabs
   survive*: Option C argues for collapsing Weekly/Monthly/Yearly into a single
   trends view, which means deleting tabs the app has always had. That is a product
   decision, not a refactor, and is the next thing to settle.
4. **What is the headline metric?** Strava leads with weekly distance. Is that
   right here, or should the top of Trends be driven by whichever metric the user
   actually trains by (time, or sessions)?

## 9. Related

- `ui/analytics/` — `AnalyticsTab.kt`, `AnalyticsOverview.kt`,
  `AnalyticsSummaries.kt`, `AnalyticsViewModel.kt`
- `data/repository/AnalyticsRepository.kt`, `data/dto/AnalyticsDtos.kt`,
  `data/network/FitPubApi.kt:296-325`
- Server (read-only reference): `analytics/boundary/AnalyticsResource.java`,
  `analytics/boundary/AchievementView.java`, `analytics/entity/ActivitySummary.java`
