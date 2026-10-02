# Review Checklist: Performance

> **Reviewer Instructions**: Validate that the changes do not degrade system latency, throughput, or memory usage.

## 🔴 Blockers (Must Fix)
- [ ] **O(N^2) or Worse**: Are there nested loops over large datasets that could be optimized with HashMaps/Sets?
- [ ] **Memory Leaks**: Are large objects held in memory indefinitely? Are event listeners properly deregistered?
- [ ] **Unpaginated Queries**: Does an endpoint return an unbounded list from the database?
- [ ] **Synchronous I/O**: Are network or file system calls blocking the main thread (especially in Node.js or Python)?

## 🟡 Warnings (Should Fix)
- [ ] **Cache Utilization**: Could this highly-read, rarely-written data be cached (Redis/Memcached)?
- [ ] **Over-Fetching**: Is the backend sending large payloads when the client only needs a few fields?
- [ ] **Frontend Bundle Size**: Does this introduce a massive third-party library? Can it be lazy-loaded?
- [ ] **Index Usage**: Will the new database queries trigger full table scans on large tables?

## 🟢 Advisories (Nice to Have)
- [ ] **Compression**: Are large payloads compressed (gzip/brotli)?
- [ ] **Connection Pooling**: Are HTTP clients reusing connections via keep-alive?
