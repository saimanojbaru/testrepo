import { useRestAPI } from "./useRestAPI";
import Header from "./Header";
import OptionChainTable from "./OptionChainTable";

const App = () => {
  const {
    connected,
    mode,
    messageCount,
    indices,
    options,
    connectionError,
    lastUpdate,
    getIndexData,
    getOptionChainData,
    refreshData,
  } = useRestAPI();

  const niftyData = getIndexData("NIFTY");
  const bankniftyData = getIndexData("BANKNIFTY");
  const sensexData = getIndexData("SENSEX");

  const niftyChain = getOptionChainData("NIFTY");
  const bankniftyChain = getOptionChainData("BANKNIFTY");
  const sensexChain = getOptionChainData("SENSEX");

  return (
    <div className="app-container">
      <Header
        connected={connected}
        mode={mode}
        messageCount={messageCount}
        indices={indices}
        options={options}
        lastUpdate={lastUpdate}
        onRefresh={refreshData}
      />

      {/* Connection Error Banner */}
      {!connected && connectionError && (
        <div className="error-banner">
          <div className="error-content">
            <div className="error-icon">
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="#dc2626"
                strokeWidth="2"
              >
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            </div>
            <div>
              <div className="error-title">Connection Failed</div>
              <div className="error-message">{connectionError}</div>
            </div>
          </div>
          <button
            onClick={() =>
              window.open(
                "https://nifty-backend-claude.onrender.com/login",
                "_blank"
              )
            }
            className="error-action-btn"
          >
            Login to Backend
          </button>
        </div>
      )}

      <div className="main-content">
        {/* Option Chains Section */}
        <div className="option-chains-grid">
          <OptionChainTable
            indexName="NIFTY"
            chainData={niftyChain}
            indexData={niftyData}
          />

          <OptionChainTable
            indexName="BANKNIFTY"
            chainData={bankniftyChain}
            indexData={bankniftyData}
          />

          <OptionChainTable
            indexName="SENSEX"
            chainData={sensexChain}
            indexData={sensexData}
          />
        </div>
      </div>
    </div>
  );
};

export default App;
