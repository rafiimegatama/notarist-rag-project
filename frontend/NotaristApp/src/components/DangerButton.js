import React from 'react';
import Button from './Button';

/** Semantic alias over Button's danger variant — destructive actions (reject, delete, logout). */
export default function DangerButton(props) {
  return <Button variant="danger" {...props} />;
}
