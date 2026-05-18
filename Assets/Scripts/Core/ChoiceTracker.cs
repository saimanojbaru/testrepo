using System;
using System.Collections.Generic;

namespace CorporateDragon.Core
{
    [Serializable]
    public class ChoiceSnapshot
    {
        public List<string> flags = new List<string>();
        public List<string> choiceIds = new List<string>();
    }

    public class ChoiceTracker
    {
        readonly HashSet<string> _flags = new HashSet<string>();
        readonly List<string> _history = new List<string>();

        public event Action OnChanged;

        public bool HasFlag(string flag) => !string.IsNullOrEmpty(flag) && _flags.Contains(flag);

        public void SetFlag(string flag)
        {
            if (string.IsNullOrEmpty(flag)) return;
            if (_flags.Add(flag)) OnChanged?.Invoke();
        }

        public void SetFlags(IEnumerable<string> flags)
        {
            if (flags == null) return;
            bool changed = false;
            foreach (var f in flags)
                if (!string.IsNullOrEmpty(f) && _flags.Add(f)) changed = true;
            if (changed) OnChanged?.Invoke();
        }

        public void RecordChoice(string id) { if (!string.IsNullOrEmpty(id)) _history.Add(id); }

        public IReadOnlyList<string> History => _history;
        public IReadOnlyCollection<string> Flags => _flags;

        public ChoiceSnapshot Serialize()
        {
            var s = new ChoiceSnapshot();
            s.flags.AddRange(_flags);
            s.choiceIds.AddRange(_history);
            return s;
        }

        public void Load(ChoiceSnapshot s)
        {
            _flags.Clear(); _history.Clear();
            if (s == null) return;
            foreach (var f in s.flags) _flags.Add(f);
            _history.AddRange(s.choiceIds);
            OnChanged?.Invoke();
        }
    }
}
