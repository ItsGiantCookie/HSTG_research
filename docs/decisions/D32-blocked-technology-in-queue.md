# D32 — What a blocked technology does to the queue

Status: **Decided** (Hendrik, 2026-11-24) · Needed by: WP-08

## Question
When History Stages refuses our unlock (logic block, lost for good, or a `BeforeUnlock` veto), §4.1
marks the technology BLOCKED, keeps its progress and says "the queue moves on". All labs work on the
queue head (D18). Where does the blocked technology go?

## Options
1. **Move to the back.** Stays queued and keeps its progress; the labs continue with the next
   entry. It is retried on every stage event and every 100 ticks. Nothing the player queued
   disappears.
2. **Remove from the queue.** Leaves the queue but keeps its progress and the BLOCKED mark. The
   player re-adds it when the block is gone; queued research never waits on something that may stay
   blocked.
3. **Stay at the head.** All labs idle with reason BLOCKED until HS accepts. The strictest reading of
   D18, but one vetoed stage can stall a team's research indefinitely.

## Decision
Option 2: remove from the queue.

## Consequences
- `core.ResearchBook.blocked` removes the technology from queue and pending unlocks, keeps its
  progress and marks it BLOCKED.
- `ResearchBook.enqueue` clears the mark. If the technology is already complete, it becomes pending
  again, so it is retried at once.
- There is no automatic retry while it is out of the queue. The lab screen (WP-10) should show
  blocked technologies so the player knows to re-add them.
- The scheduler's `BLOCKED` idle reason stays for addons. Our own blocked technologies never sit at
  the head.
