import { useState, useEffect, useCallback, useRef } from "react";

export const useWebSocket = (url) => {
  const [data, setData] = useState({
    indices: {},
    options: {},
    mode: null,
    connected: false,
    messageCount: 0,
    connectionError: null,
  });

  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const reconnectAttemptsRef = useRef(0);
  const mountedRef = useRef(true);

  const connect = useCallback(() => {
    // Don't connect if component is unmounted
    if (!mountedRef.current) return;

    // Clear any existing connection
    if (
      wsRef.current?.readyState === WebSocket.OPEN ||
      wsRef.current?.readyState === WebSocket.CONNECTING
    ) {
      console.log("⚠️ Connection already exists, skipping...");
      return;
    }

    console.log("🔌 Connecting to WebSocket...");

    try {
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        console.log("✅ WebSocket connected");
        if (mountedRef.current) {
          setData((prev) => ({
            ...prev,
            connected: true,
            connectionError: null,
          }));
          reconnectAttemptsRef.current = 0;
        }
      };

      ws.onclose = (event) => {
        console.log("❌ WebSocket disconnected", event.code, event.reason);

        let errorMessage = null;
        if (event.code === 1006) {
          errorMessage =
            "Connection failed. Backend may be sleeping or requires login.";
        } else if (event.code === 1008) {
          errorMessage = "Connection rejected. Please login to the backend.";
        } else if (event.code !== 1000) {
          errorMessage = `Connection closed with code ${event.code}`;
        }

        if (mountedRef.current) {
          setData((prev) => ({
            ...prev,
            connected: false,
            connectionError: errorMessage,
          }));

          // Only reconnect if component is still mounted and it wasn't a normal closure
          if (mountedRef.current && event.code !== 1000) {
            reconnectAttemptsRef.current += 1;

            // Increase delay for repeated failures
            const delay = Math.min(2000 * reconnectAttemptsRef.current, 10000);

            console.log(
              `🔄 Reconnecting in ${delay}ms... (attempt ${reconnectAttemptsRef.current})`
            );

            reconnectTimeoutRef.current = setTimeout(() => {
              if (mountedRef.current) {
                connect();
              }
            }, delay);
          }
        }
      };

      ws.onerror = (error) => {
        console.error("❌ WebSocket error:", error);
        if (mountedRef.current) {
          setData((prev) => ({
            ...prev,
            connectionError: "WebSocket error occurred. Check backend status.",
          }));
        }
      };

      ws.onmessage = (event) => {
        if (!mountedRef.current) return;

        try {
          const message = JSON.parse(event.data);

          if (message.type === "ENGINE_MODE") {
            setData((prev) => ({ ...prev, mode: message.mode }));
          } else if (message.type === "INDEX_SIGNAL") {
            setData((prev) => ({
              ...prev,
              indices: {
                ...prev.indices,
                [message.symbol]: message,
              },
              messageCount: prev.messageCount + 1,
            }));
          } else if (message.type === "OPTION_SIGNAL") {
            setData((prev) => ({
              ...prev,
              options: {
                ...prev.options,
                [message.symbol]: message,
              },
              messageCount: prev.messageCount + 1,
            }));
          }
        } catch (error) {
          console.error("Error parsing message:", error);
        }
      };
    } catch (error) {
      console.error("Error creating WebSocket:", error);
      if (mountedRef.current) {
        setData((prev) => ({
          ...prev,
          connectionError: "Failed to create WebSocket connection",
        }));
      }
    }
  }, [url]);

  useEffect(() => {
    mountedRef.current = true;
    connect();

    return () => {
      console.log("🧹 Cleaning up WebSocket connection...");
      mountedRef.current = false;

      // Clear reconnect timeout
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }

      // Close WebSocket connection properly
      if (wsRef.current) {
        if (wsRef.current.readyState === WebSocket.OPEN) {
          wsRef.current.close(1000, "Component unmounting");
        }
        wsRef.current = null;
      }
    };
  }, [connect]);

  const getIndexData = useCallback(
    (indexName) => {
      return data.indices[indexName] || null;
    },
    [data.indices]
  );

  const getOptionsByIndex = useCallback(
    (indexName) => {
      return Object.values(data.options).filter(
        (opt) => opt.name === indexName
      );
    },
    [data.options]
  );

  const getOptionChainData = useCallback(
    (indexName) => {
      const options = getOptionsByIndex(indexName);

      // Group by strike
      const strikeMap = {};
      options.forEach((opt) => {
        if (!strikeMap[opt.strike]) {
          strikeMap[opt.strike] = { strike: opt.strike };
        }
        if (opt.optionType === "CE") {
          strikeMap[opt.strike].call = opt;
        } else {
          strikeMap[opt.strike].put = opt;
        }
      });

      return Object.values(strikeMap).sort((a, b) => a.strike - b.strike);
    },
    [getOptionsByIndex]
  );

  const getAllOptions = useCallback(() => {
    return Object.values(data.options);
  }, [data.options]);

  return {
    ...data,
    getIndexData,
    getOptionsByIndex,
    getOptionChainData,
    getAllOptions,
  };
};
