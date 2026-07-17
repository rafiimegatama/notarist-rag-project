import React from 'react';
import Button from './Button';

/** Semantic alias over Button's secondary variant — outlined, for non-primary actions. */
export default function SecondaryButton(props) {
  return <Button variant="secondary" {...props} />;
}
