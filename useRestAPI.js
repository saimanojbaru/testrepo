import { useState, useEffect, useCallback, useRef } from "react";

const CONFIG = {
  // Auto-detect environment
  API_BASE_URL: "https://nifty-backend-claude.onrender.com",

  FETCH_INTERVAL: 30000, // 30 seconds
  RETRY_DELAY: 5000, // 5 seconds
  MAX_RETRIES: 3,
};

export const useRestAPI = () => {
  const [data, setData] = useState({
    indices: {},
    options: {},
    mode: null,
    connected: false,
    messageCount: 0,
    connectionError: null,
    lastUpdate: null,
  });

  const intervalRef = useRef(null);
  const mountedRef = useRef(true);
  const retryCountRef = useRef(0);

  const fetchWithRetry = useCallback(
    async (url, retries = CONFIG.MAX_RETRIES) => {
      for (let i = 0; i < retries; i++) {
        try {
          const response = await fetch(url);

          if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
          }

          return await response.json();
        } catch (error) {
          console.error(`Attempt ${i + 1}/${retries} failed:`, error.message);

          if (i === retries - 1) {
            throw error; // Last attempt failed
          }

          // Wait before retry
          await new Promise((resolve) =>
            setTimeout(resolve, CONFIG.RETRY_DELAY)
          );
        }
      }
    },
    []
  );

  const fetchData = useCallback(async () => {
    if (!mountedRef.current) return;

    try {
      console.log(`🔄 Fetching data... [${new Date().toLocaleTimeString()}]`);

      const response = await fetchWithRetry(`${CONFIG.API_BASE_URL}/api/data`);

      if (!mountedRef.current) return;

      if (response && response.indices && response.options) {
        // Transform indices array to object
        const indicesObj = {};
        response.indices.forEach((index) => {
          indicesObj[index.symbol] = index;
        });

        // Transform options array to object
        const optionsObj = {};
        response.options.forEach((option) => {
          optionsObj[option.symbol] = option;
        });

        setData((prev) => ({
          indices: indicesObj,
          options: optionsObj,
          mode: response.marketStatus === "OPEN" ? "LIVE" : "CLOSED",
          connected: true,
          messageCount: prev.messageCount + 1,
          connectionError: null,
          lastUpdate: new Date(response.timestamp),
        }));

        retryCountRef.current = 0;
        console.log(`✅ Data updated [${response.timestamp}]`);
        console.log(`   Market: ${response.marketStatus}`);
        console.log(`   Indices: ${response.stats.totalIndices}`);
        console.log(`   Options: ${response.stats.totalOptions}`);
      }
    } catch (error) {
      console.error("❌ Failed to fetch data:", error);

      if (!mountedRef.current) return;

      retryCountRef.current += 1;

      let errorMessage = "Connection failed. ";

      if (error.message.includes("503")) {
        errorMessage += "Backend not initialized. Please login.";
      } else if (error.message.includes("Failed to fetch")) {
        errorMessage += "Backend may be sleeping or offline.";
      } else {
        errorMessage += error.message;
      }

      setData((prev) => ({
        ...prev,
        connected: false,
        connectionError: errorMessage,
      }));
    }
  }, [fetchWithRetry]);

  useEffect(() => {
    mountedRef.current = true;
    console.log(`🚀 REST API initialized: ${CONFIG.API_BASE_URL}`);
    console.log(`⏱️  Polling interval: ${CONFIG.FETCH_INTERVAL / 1000}s`);

    // Initial fetch
    fetchData();

    // Set up polling interval
    intervalRef.current = setInterval(() => {
      if (mountedRef.current && !document.hidden) {
        fetchData();
      }
    }, CONFIG.FETCH_INTERVAL);

    // Handle visibility change (pause when tab hidden)
    const handleVisibilityChange = () => {
      if (document.hidden) {
        console.log("⏸️  Polling paused (tab hidden)");
      } else {
        console.log("▶️  Polling resumed (tab visible)");
        fetchData(); // Fetch immediately when tab becomes visible
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      console.log("🧹 Cleaning up REST API polling...");
      mountedRef.current = false;

      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }

      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [fetchData]);

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

  const refreshData = useCallback(() => {
    console.log("🔄 Manual refresh triggered");
    fetchData();
  }, [fetchData]);

  return {
    ...data,
    getIndexData,
    getOptionsByIndex,
    getOptionChainData,
    getAllOptions,
    refreshData,
  };
};
