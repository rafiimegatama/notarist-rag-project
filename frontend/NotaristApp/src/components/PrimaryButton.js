import React from 'react';
import Button from './Button';

/** Semantic alias over Button's primary variant — the app's default call to action. */
export default function PrimaryButton(props) {
  return <Button variant="primary" {...props} />;
}
