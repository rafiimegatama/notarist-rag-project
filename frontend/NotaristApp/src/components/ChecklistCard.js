import React from 'react';
import Card from './Card';
import ChecklistItem from './ChecklistItem';

/** A ChecklistItem wrapped in a Card — the standard container used in Verification / QC lists. */
export default function ChecklistCard({ title, subtitle, decision, comment, onDecide, onComment, showComment = true, style }) {
  return (
    <Card style={style}>
      <ChecklistItem
        title={title}
        subtitle={subtitle}
        decision={decision}
        comment={comment}
        onDecide={onDecide}
        onComment={onComment}
        showComment={showComment}
      />
    </Card>
  );
}
