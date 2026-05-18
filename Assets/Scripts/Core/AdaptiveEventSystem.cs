using System.Collections.Generic;
using CorporateDragon.Data;

namespace CorporateDragon.Core
{
    /// Filters episode lines by current flag state.
    public class AdaptiveEventSystem
    {
        readonly ChoiceTracker _tracker;

        public AdaptiveEventSystem(ChoiceTracker tracker) { _tracker = tracker; }

        public List<LineData> FilterLines(IEnumerable<LineData> lines)
        {
            var result = new List<LineData>();
            if (lines == null) return result;
            foreach (var ln in lines)
            {
                if (!string.IsNullOrEmpty(ln.ifFlag) && !_tracker.HasFlag(ln.ifFlag)) continue;
                if (!string.IsNullOrEmpty(ln.ifNotFlag) && _tracker.HasFlag(ln.ifNotFlag)) continue;
                result.Add(ln);
            }
            return result;
        }
    }
}
