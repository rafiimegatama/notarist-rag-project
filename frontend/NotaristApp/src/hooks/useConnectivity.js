// Subscribes a component to the transport-layer connectivity signal (Sprint 4, Task 8).
//
// Thin by design: api/connectivity.js owns the state, this only bridges it into React. No Context,
// no provider, no store — the signal is a property of the network, not of the component tree, and
// every subscriber wants the same answer.

import { useEffect, useState } from 'react';
import { subscribe, getNetworkState } from '../api/connectivity';

export default function useConnectivity() {
  const [state, setState] = useState(getNetworkState);

  useEffect(() => {
    // Re-read on mount: the status may have changed between the initial useState and this effect.
    setState(getNetworkState());
    return subscribe(setState);
  }, []);

  return state;
}
