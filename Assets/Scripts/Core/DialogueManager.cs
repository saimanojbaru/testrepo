using System;
using System.Collections.Generic;
using CorporateDragon.Data;

namespace CorporateDragon.Core
{
    /// Drives one episode at a time. UI subscribes to events to render lines/choices.
    public class DialogueManager
    {
        public event Action<LineData> OnLine;
        public event Action<List<ChoiceData>> OnChoices;
        public event Action<Episode, ChoiceData> OnChoiceResolved;
        public event Action<Episode> OnEpisodeStarted;
        public event Action<Episode> OnEpisodeEnded;

        readonly AdaptiveEventSystem _adaptive;
        readonly ChoiceTracker _tracker;
        readonly StatManager _stats;
        readonly RetainerSystem _retainers;
        readonly MemoryAlbumManager _album;

        Episode _current;
        List<LineData> _visibleLines;
        int _lineIdx;

        public Episode Current => _current;

        public DialogueManager(AdaptiveEventSystem adaptive, ChoiceTracker tracker,
            StatManager stats, RetainerSystem retainers, MemoryAlbumManager album)
        {
            _adaptive = adaptive;
            _tracker = tracker;
            _stats = stats;
            _retainers = retainers;
            _album = album;
        }

        public void Begin(Episode ep)
        {
            _current = ep;
            _visibleLines = _adaptive.FilterLines(ep.lines);
            _lineIdx = 0;
            OnEpisodeStarted?.Invoke(ep);
            Advance();
        }

        public void Advance()
        {
            if (_current == null) return;
            if (_lineIdx < _visibleLines.Count)
            {
                OnLine?.Invoke(_visibleLines[_lineIdx]);
                _lineIdx++;
            }
            else
            {
                OnChoices?.Invoke(_current.choices);
            }
        }

        public void Choose(ChoiceData choice)
        {
            if (_current == null || choice == null) return;
            _stats.Apply(choice.effects);
            _tracker.SetFlags(choice.flags);
            _tracker.RecordChoice(_current.id + ":" + choice.text);
            _album.Unlock(choice.memory);
            BumpRetainerForEpisode(_current.id);
            OnChoiceResolved?.Invoke(_current, choice);
            var done = _current;
            _current = null;
            OnEpisodeEnded?.Invoke(done);
        }

        void BumpRetainerForEpisode(string id)
        {
            switch (id)
            {
                case "ch1_e1": case "ch1_e3": case "ch1_e11":
                    _retainers.Bump("maa", 1); break;
                case "ch1_e4": case "ch1_e5": case "ch1_e9":
                    _retainers.Bump("papa", 1); break;
                case "ch1_e2": case "ch1_e10":
                    _retainers.Bump("nani", 1); break;
            }
        }
    }
}
