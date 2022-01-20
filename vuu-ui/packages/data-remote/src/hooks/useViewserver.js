import { useCallback, useEffect, useRef, useState } from 'react';
import { SimpleStore } from '@vuu-ui/utils';
import { useServerConnection } from './useServerConnection';

export const addRowsFromInstruments = 'addRowsFromInstruments';
export const RpcCall = 'RPC_CALL';

const tableStore = new SimpleStore({});

const contextSortPriorities = {
  'selected-rows': 0,
  cell: 1,
  row: 2,
  grid: 3
};

const byContext = (menu1, menu2) => {
  return contextSortPriorities[menu1.context] - contextSortPriorities[menu2.context];
};

const contextCompatibleWithLocation = (location, context, selectedRowCount) => {
  switch (location) {
    case 'grid':
      if (context === 'selected-rows') {
        return selectedRowCount > 0;
      } else {
        return true;
      }
    case 'header':
      return context === 'grid';
    default:
      return false;
  }
};

const columnConfig = {
  ask: {
    name: 'ask',
    label: 'Ask',
    type: {
      name: 'number',
      renderer: { name: 'background', flashStyle: 'arrow-bg' },
      formatting: { decimals: 2, zeroPad: true }
    },
    aggregate: 'avg'
  },
  askSize: {
    name: 'askSize',
    label: 'Ask Size',
    type: {
      name: 'number'
    },
    aggregate: 'avg'
  },
  bbg: {
    name: 'bbg',
    label: 'BBG',
    type: {
      name: 'string'
    }
  },
  bid: {
    label: 'Bid',
    name: 'bid',
    type: {
      name: 'number',
      renderer: { name: 'background', flashStyle: 'arrow-bg' },
      formatting: { decimals: 2, zeroPad: true }
    },
    aggregate: 'avg'
  },
  bidSize: {
    label: 'Bid Size',
    name: 'bidSize',
    type: {
      name: 'number'
    },
    aggregate: 'avg'
  },
  close: {
    label: 'Close',
    name: 'close',
    type: {
      name: 'number',
      formatting: { decimals: 2, zeroPad: true }
    },
    aggregate: 'avg'
  },
  currency: {
    name: 'currency',
    label: 'CCY',
    width: 60
  },
  description: {
    name: 'description',
    label: 'Description',
    type: {
      name: 'string'
    }
  },
  exchange: {
    name: 'exchange',
    label: 'Exchange',
    type: {
      name: 'string'
    }
  },
  filledQuantity: {
    name: 'filledQuantity',
    label: 'filled qty',
    width: 80,
    type: {
      name: 'number',
      renderer: { name: 'progress', associatedField: 'quantity' },
      format: { decimals: 0 }
    }
  },
  isin: {
    name: 'isin',
    label: 'ISIN',
    type: {
      name: 'string'
    }
  },
  last: {
    label: 'Last',
    name: 'last',
    type: {
      name: 'number',
      formatting: { decimals: 2, zeroPad: true }
    },
    aggregate: 'avg'
  },
  lotSize: {
    label: 'Lot Size',
    name: 'lotSize',
    width: 80,
    type: {
      name: 'number'
    }
  },
  open: {
    label: 'Open',
    name: 'open',
    type: {
      name: 'number',
      formatting: { decimals: 2, zeroPad: true }
    },
    aggregate: 'avg'
  },
  phase: {
    label: 'Phase',
    name: 'phase',
    type: {
      name: 'string'
    }
  },
  ric: {
    name: 'ric',
    label: 'RIC',
    type: {
      name: 'string'
    }
  },
  quantity: {
    name: 'quantity',
    width: 80,
    type: {
      name: 'number'
    }
  },
  scenario: {
    label: 'Scenario',
    name: 'scenario',
    type: {
      name: 'string'
    }
  }
};

const extendSchema = ({ columns, dataTypes, table }) => {
  return {
    table,
    columns: columns.map((col, idx) =>
      columnConfig[col]
        ? { ...columnConfig[col], serverDataType: dataTypes[idx] }
        : { name: col, serverDataType: dataTypes[idx] }
    )
  };
};

// Either a DataSource or a Connection is acceptable as rpcServer, both support the rpc interface
export const useViewserver = ({ rpcServer, onConfigChange, onRpcResponse } = {}) => {
  const [tables, setTables] = useState(tableStore.value);
  const server = useServerConnection();
  const contextMenu = useRef();
  const visualLinks = useRef();

  const buildTables = useCallback((schemas) => {
    const newTables = {};
    schemas.forEach((schema) => {
      newTables[schema.table.table] = extendSchema(schema);
    });
    tableStore.value = newTables;
  }, []);

  useEffect(() => {
    tableStore.on('loaded', (_, tables) => {
      setTables(tables);
    });
  }, []);

  const makeRpcCall = useCallback(
    async (options) => {
      const response = await server.rpcCall(options);
      switch (response.method) {
        case addRowsFromInstruments:
          if (!response.orderEntryOpen) {
            onRpcResponse && onRpcResponse('showOrderEntry');
          } else {
            console.log('select entries in orderEntry');
          }
          break;
        default:
          console.log(`response from unexpected method ${response.method}`);
      }
    },
    [onRpcResponse, server]
  );

  const buildViewserverMenuOptions = useCallback((location, options) => {
    const { selectedRowCount = 0 } = options;
    const descriptors = [];

    if (visualLinks.current) {
      visualLinks.current.forEach((linkDescriptor) => {
        descriptors.push({
          label: `Link to ${linkDescriptor.link.toTable}`,
          action: 'link-table',
          options: linkDescriptor
        });
      });
    }

    if (contextMenu.current) {
      contextMenu.current.menus.sort(byContext).forEach(({ name, filter, rpcName, context }) => {
        if (contextCompatibleWithLocation(location, context, selectedRowCount)) {
          descriptors.push({
            label: name,
            action: 'MENU_RPC_CALL',
            options: {
              context,
              filter,
              rpcName
            }
          });
        }
      });
    }

    return descriptors;
  }, []);

  const dispatchGridAction = useCallback(
    (action) => {
      if (action.type === 'VIEW_PORT_MENUS_RESP') {
        contextMenu.current = action.menu;
        return true;
      } else if (action.type === 'VP_VISUAL_LINKS_RESP') {
        visualLinks.current = action.links;
        return true;
      } else if (action.type === 'visual-link-created') {
        onConfigChange?.(action);
      } else {
        console.log(`useViewserver dispatchGridAction no handler for $s{action.type}`);
      }
    },
    [onConfigChange]
  );

  const handleMenuAction = useCallback(
    (type, options) => {
      if (type === 'MENU_RPC_CALL') {
        rpcServer.rpcCall({ type, ...options }).then((result) => {
          onRpcResponse && onRpcResponse(result);
        });
        return true;
      } else if (type === 'link-table') {
        // the createLink method only exists on dataSource
        return rpcServer.createLink(options), true;
      } else {
        console.log(`useViewServer handleMenuAction,  can't handle ${type}`);
      }
    },
    [rpcServer, onRpcResponse]
  );

  useEffect(() => {
    async function fetchTableMetadata() {
      tableStore.status = 'loading';
      const { tables } = await server.getTableList();
      buildTables(
        await Promise.all(tables.map((tableDescriptor) => server.getTableMeta(tableDescriptor)))
      );
    }

    if (server && tableStore.status === '') {
      fetchTableMetadata();
    }
  }, [buildTables, server, setTables]);

  return {
    buildViewserverMenuOptions,
    dispatchGridAction,
    handleMenuAction,
    tables,
    makeRpcCall
  };
};