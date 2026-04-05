# Xstore vs SIM Workbench Guidance

Use this document when improving the Xstore vs SIM user experience.

The goal is not to turn the lane into a bigger dashboard. The goal is to turn it into a retailer-facing reconciliation workbench that helps users understand lane health, prioritize issues, investigate transactions, and move work into case handling.

## Product Position

Xstore vs SIM is a primary business page.

It should feel:

- retailer-facing
- action-oriented
- explanation-heavy
- operationally useful

It should not feel like:

- a technical admin console
- a passive report board
- a page full of disconnected metrics

Primary users:

- store operations
- finance reconciliation teams
- inventory control teams
- audit and support users

## Core Design Principles

1. Show business meaning before technical detail.
   Use business labels first. Keep technical codes as supporting text where useful.

2. Make important metrics actionable.
   Metric cards should open filtered records, queue views, or drill-down paths.

3. Prioritize work, not just counts.
   Users should immediately see what is overdue, risky, or blocking operations.

4. Keep the main screen focused on triage.
   Trends and analytics are useful, but they should not dominate the main workbench.

5. Reduce dash-heavy or zero-heavy UI.
   Use contextual empty states and collapse low-value sections when possible.

6. Preserve the current premium SaaS visual language.
   Improve structure and usability without redesigning the whole shell.

## 1. Xstore vs SIM Results Page

### Target Outcome

This page should help a user answer five questions quickly:

1. Is the lane healthy?
2. What needs attention first?
3. Which exception types are driving the problem?
4. Which transactions require review?
5. What should I do next?

### Recommended Page Structure

Top area:

- filters
- explicit Apply / Refresh action
- lane health / interpretation banner

Main workbench tab:

- reconciliation summary
- exception metrics
- results table
- transaction review panel or drawer

Secondary tab:

- Trends & SLA
- match trend
- exception trend
- aging
- top failing stores
- top failing registers
- SLA metrics

### Keep

- Store / Register / From Date / To Date filters
- reconciliation summary
- exception metrics
- results table
- selected record / transaction review area
- export capability
- operational trend content

### Improve

#### A. Lane Health / Business Interpretation

Add a compact interpretation banner near the top.

Example:

- Lane Health: Attention Required
- 4 transactions are still pending in SIM
- No missing transactions detected
- No quantity mismatches detected
- No duplicate transactions detected

This banner should summarize what matters, not just restate raw numbers.

#### B. Reconciliation Summary Model

The summary must account for the total population clearly.

Recommended summary cards:

- Total Transactions
- Matched
- Pending Processing
- Exceptions
- Missing in SIM
- Match Rate
- Exception Rate

Optional only if the data exists:

- Excluded / Ignored
- Unmatched but not yet case-worthy

Important rule:

- A user should not have to guess where the rest of the transaction population went.

#### C. Exception Metrics

Keep the section, but make it operational.

Each tile should ideally show:

- count
- severity color
- aged / past-SLA count
- delta vs previous day if available
- click behavior that opens filtered records

Examples:

- Quantity Mismatch
  12 open
  4 past SLA
  +3 vs yesterday

- Pending in SIM
  4 open
  oldest pending 47m
  click to filter

#### D. Filters

Current filters are good but incomplete.

Add if supported by the API:

- Status
- Exception Type
- Match Band
- Only Open Exceptions
- Priority / SLA State

Also make it clear whether filters auto-run or require Apply.
If the page does not auto-run clearly, add an explicit Apply / Refresh button.

#### E. Status Language

Do not show raw system status alone.

Use business-friendly labels with technical detail as supporting text.

Examples:

- Pending in SIM
  `PROCESSING_PENDING_IN_SIM`

- Awaiting SIM processing
  `AWAITING_SIM`

- Missing in SIM
  `MISSING_IN_SIM`

Also add tooltip text where possible.

Example tooltip:

- Awaiting SIM processing
  Transaction exists in Xstore and is still awaiting downstream processing in SIM.

#### F. Results Table

This is the core of the page. It should support triage, not just display facts.

Recommended additions:

- priority / severity indicator
- exception type column
- age / waiting time column
- action column

Suggested row actions:

- View
- Drill-down
- Open Queue
- Assign
- Add Note

If full workflow actions are not ready, keep the action column and add placeholders that route into existing queue/detail paths.

#### G. Transaction Review Area

Rename "Selected Record" to something more useful, such as:

- Transaction Review

Keep the lightweight review pattern, but add more business context:

- why the transaction is flagged
- exception reason
- source vs target summary
- last event timestamp
- processing stage
- suggested next action
- linked case / queue status if it exists

Suggested next action examples:

- Pending in SIM: Monitor for completion; escalate if it remains pending past SLA.
- Quantity mismatch: Review line-level quantity differences.
- Missing in SIM: Validate publish and downstream processing path.
- Duplicate in SIM: Escalate to exception queue for investigation.

#### H. Export Controls

Keep export, but reduce its visual weight.

Preferred pattern:

- one quieter Export dropdown

instead of:

- multiple top-level Excel / CSV / PDF buttons competing with workbench actions

### De-emphasize on the Main Tab

- large trend sections
- oversized zero-value cards
- repeated empty-state containers

These belong in the Trends & SLA tab or should collapse when there is no useful signal.

## 2. Transaction Drill-down Page

### Target Outcome

This page should explain the problem, not just list metadata.

The user should understand:

- what is wrong
- why it is wrong
- how serious it is
- what to do next

### Keep

- header with transaction identity
- status chip
- action buttons
- transaction context
- related exception case
- variance signals
- line discrepancy content

### Improve

#### A. Investigation Summary

Add a summary block near the top with:

- business status label
- status explanation
- business impact
- time in current state
- SLA state
- recommended next action

Example:

- Status: Pending in SIM
- Meaning: Transaction exists in Xstore but is still awaiting downstream completion in SIM
- SLA: Within SLA
- Next Action: Monitor now; escalate if still pending after threshold

#### B. Source vs Target Comparison

This is a high-value improvement and should be treated as core, not optional.

Show a side-by-side comparison for:

- transaction presence
- amount
- quantity
- line count
- timestamps
- processing state

Highlight differences clearly.

#### C. Related Case / Queue Context

If no case exists:

- show "No exception case exists yet"
- show CTA: Create Exception Case / Send to Exception Queue

If a case exists:

- show case id
- owner
- status
- SLA
- open case action

#### D. Variance Signals

Group variance signals into categories:

- Match signals
- Amount signals
- Quantity signals
- Tolerance signals
- Diagnostics

Use clear visual states:

- pass
- fail
- unknown
- not applicable

#### E. Timeline / Processing History

Add an event timeline or history strip if data is available.

Example:

- Xstore captured transaction
- published to reconciliation lane
- reconciliation executed
- awaiting SIM
- last checked timestamp

#### F. Advanced Diagnostics

Move low-level technical detail into a collapsible advanced section.

Examples:

- checksum
- tolerance profile
- match rule
- low-level diagnostics

#### G. Better Empty State Language

Avoid bare dashes.

Use text like:

- Not available yet
- Not applicable
- Not calculated
- No discrepancy detected
- Awaiting downstream completion

## 3. Exception Queue Page

### Target Outcome

This page should feel like a case triage workspace, not just a queue table.

### Keep

- top KPI summary
- filters
- search
- queue records table
- store incidents concept

### Improve

#### A. Direct Case Actions

Add row actions where supported:

- Open Case
- Assign
- Assign to Me
- Escalate
- Add Note
- Resolve
- Snooze / Defer

#### B. Bulk Actions

When rows are selected, show a bulk action toolbar.

Examples:

- Assign Selected
- Escalate Selected
- Mark Under Review
- Resolve Selected
- Merge into Incident

#### C. Lifecycle States

Use a clearer workflow:

- New
- Unassigned
- Assigned
- Under Review
- Awaiting Business Input
- Awaiting Technical Resolution
- Resolved
- Closed
- Reopened

#### D. SLA / Aging Visibility

Show:

- age
- due by
- overdue by
- last updated
- last touched

#### E. Priority Reason

Make priority explanation human-readable.

Examples:

- Overdue by 2 days
- Affects store 1007
- Recurring for 3 days
- High business impact
- No owner assigned

#### F. Filters and Saved Views

Useful additions:

- SLA State
- Priority
- Store
- Business Date
- Aging Bucket

Useful saved views:

- My Work
- Unassigned
- Overdue
- High Impact
- Store Incidents
- Recently Updated

## 4. Cross-Page Interaction Rules

Use these rules consistently across Results, Drill-down, and Queue.

### Metrics

- summary cards explain lane state
- exception cards open filtered records
- zero-value tiles should stay visually quiet

### Statuses

- business label first
- technical code second
- tooltip explanation where helpful

### Empty States

Avoid:

- bare dashes
- blank panels
- large empty sections

Prefer:

- contextual text
- next-step CTA
- compact layout when no data exists

### Priority

Introduce a prioritization layer wherever possible:

- Critical
- High
- Medium
- Low

or

- Past SLA
- Due Today
- Pending
- Informational

### Workflow Entry Points

The UI should consistently allow movement between:

- Results
- Drill-down
- Exception Queue

That movement should feel native, not bolted on.

## 5. Recommended Phasing

### Phase 1

High-value workbench improvements:

- add lane health / interpretation banner
- fix summary population model
- make exception metrics clickable
- improve status language
- add explicit Apply / Refresh behavior
- add row-level action column
- improve transaction review context
- add SLA / aging cues

### Phase 2

Investigation and workflow improvements:

- split Trends & SLA into a secondary tab
- add source vs target comparison
- add timeline / event history
- strengthen create/open case CTA
- improve queue actions and saved views

### Phase 3

Advanced operating model:

- bulk queue actions
- advanced diagnostics collapse
- replay eligibility
- root cause hints
- smarter assignment suggestions

## 6. Acceptance Criteria

Use these as a quality check.

### Results Page

- A user can understand lane health in under 10 seconds.
- Summary cards account for the visible transaction population clearly.
- Exception metrics drive action, not just observation.
- The table helps identify which rows need attention first.
- Trends do not dominate the main workbench.

### Drill-down

- A user can understand why a transaction is flagged without reading raw technical fields.
- Source vs target differences are visible and easy to compare.
- The next action is clear.

### Exception Queue

- A user can triage, assign, and progress work directly from the queue.
- SLA and aging are visible enough to support prioritization.
- The queue supports both individual and grouped store-level workflows.

## 7. Constraints

- Do not break existing routing.
- Do not remove current data fields; reorganize and prioritize them.
- Preserve the current premium SaaS visual style.
- Keep the pages usable for both retailer business users and support users.
- Prefer incremental improvement over a full redesign unless the existing structure blocks usability.
