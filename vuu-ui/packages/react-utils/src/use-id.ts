import { useMemo } from 'react';
import { uuid } from '@vuu-ui/utils';

export const useId = (idProp?: string) => {
  const id = useMemo(() => {
    return idProp || uuid(5);
  }, [idProp]);
  return id;
};
