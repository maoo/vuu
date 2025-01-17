import { useCallback, useRef, useState } from 'react';

export function useControlled({ controlled, default: defaultProp }) {
  const { current: isControlled } = useRef(controlled !== undefined);
  const [valueState, setValue] = useState(defaultProp);
  const value = isControlled ? controlled : valueState;
  const setValueIfUncontrolled = useCallback(
    (newValue) => {
      if (!isControlled) {
        setValue(newValue);
      }
    },
    [isControlled]
  );

  return [value, setValueIfUncontrolled, isControlled];
}
