import React from 'react';
import { Component, registerComponent } from '@vuu-ui/layout';

export const Red = ({ style }) => {
  return <Component style={{ ...style, backgroundColor: 'red' }} />;
};

registerComponent('Red', Red);
