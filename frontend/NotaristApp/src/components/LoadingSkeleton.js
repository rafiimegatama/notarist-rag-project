import React from 'react';
import { SkeletonList } from './Skeleton';

/**
 * Standard list loading placeholder used by every list screen (cases, bundles, reminders,
 * conversations). Thin alias over SkeletonList so screens share one loading look.
 */
export default function LoadingSkeleton({ count = 6 }) {
  return <SkeletonList count={count} />;
}
