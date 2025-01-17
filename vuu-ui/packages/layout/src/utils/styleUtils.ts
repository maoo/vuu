import { CSSProperties } from 'react';
export type CSSFlexProperties = Pick<CSSProperties, 'flexBasis' | 'flexGrow' | 'flexShrink'>;

export const expandFlex = (flex: number | CSSFlexProperties): CSSFlexProperties => {
  if (typeof flex === 'number') {
    return {
      flexBasis: 0,
      flexGrow: 1,
      flexShrink: 1
    };
  } else {
    throw Error(`"no support yet for flex value ${flex}`);
  }
};
