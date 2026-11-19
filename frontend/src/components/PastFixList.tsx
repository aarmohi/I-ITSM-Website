import { Link } from 'react-router-dom'
import type { PastFix } from '../api/types'
import { timeAgo } from '../lib/format'

/**
 * Fixes from resolved incidents that look like the report being written. The fix itself is always
 * shown; the original incident's title and link only when the viewer may open it.
 */
export function PastFixList({ fixes }: { fixes: PastFix[] }) {
  return (
    <ul className="past-fix-list">
      {fixes.map((fix) => (
        <li key={fix.reference}>
          <div className="past-fix-head">
            {fix.accessible && fix.incidentId != null ? (
              <Link to={`/incidents/${fix.incidentId}`} target="_blank">
                {fix.reference} · {fix.title}
              </Link>
            ) : (
              <span>{fix.reference} · a similar incident reported by someone else</span>
            )}
          </div>
          <p>{fix.resolutionNotes}</p>
          <span className="past-fix-meta">
            {fix.serviceName}
            {fix.resolvedAt && <> · fixed {timeAgo(fix.resolvedAt)}</>} · {Math.round(fix.score * 100)}% match
          </span>
        </li>
      ))}
    </ul>
  )
}
