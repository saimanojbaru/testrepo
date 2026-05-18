using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
using CorporateDragon.Core;
using CorporateDragon.Data;

namespace CorporateDragon.UI
{
    public class EpisodeScreen : MonoBehaviour
    {
        Image _bg;
        Text _charEmoji;
        KingsChoiceAnimator _animator;
        Text _chapterLbl, _episodeLbl, _titleLbl, _speakerLbl, _lineLbl;
        Transform _choicesParent;
        Button _continueBtn;
        Text _continueLbl;

        Episode _currentEp;
        List<LineData> _visibleLines;
        int _lineIdx;

        public static EpisodeScreen Build(Transform parent)
        {
            var go = UIHelpers.Panel("EpisodeScreen", parent, UIHelpers.Bg);
            var ep = go.AddComponent<EpisodeScreen>();

            // Stage (top half)
            var stage = UIHelpers.Box("Stage", go.transform, UIHelpers.Warm);
            var srt = stage.GetComponent<RectTransform>();
            srt.anchorMin = new Vector2(0, 0.55f); srt.anchorMax = new Vector2(1, 1f);
            srt.offsetMin = Vector2.zero; srt.offsetMax = Vector2.zero;
            ep._bg = stage.GetComponent<Image>();

            var charGO = UIHelpers.Box("Char", stage.transform, new Color(0, 0, 0, 0));
            var crt = charGO.GetComponent<RectTransform>();
            crt.anchorMin = new Vector2(0.35f, 0.05f); crt.anchorMax = new Vector2(0.65f, 0.85f);
            crt.offsetMin = Vector2.zero; crt.offsetMax = Vector2.zero;
            ep._charEmoji = UIHelpers.Label("Emoji", charGO.transform, "🧒", 240, Color.white, TextAnchor.MiddleCenter);
            UIHelpers.SetAnchor(ep._charEmoji.rectTransform, Vector2.zero, Vector2.one);
            ep._animator = charGO.AddComponent<KingsChoiceAnimator>();

            // Panel (bottom half)
            var panel = UIHelpers.Box("Panel", go.transform, UIHelpers.Card);
            var prt = panel.GetComponent<RectTransform>();
            prt.anchorMin = new Vector2(0, 0f); prt.anchorMax = new Vector2(1, 0.55f);
            prt.offsetMin = Vector2.zero; prt.offsetMax = Vector2.zero;

            ep._chapterLbl = UIHelpers.Label("Ch", panel.transform, "Chapter 1", 24, UIHelpers.InkSoft, TextAnchor.UpperLeft);
            var chrt = ep._chapterLbl.rectTransform;
            chrt.anchorMin = new Vector2(0.04f, 0.92f); chrt.anchorMax = new Vector2(0.5f, 0.98f);
            chrt.offsetMin = Vector2.zero; chrt.offsetMax = Vector2.zero;

            ep._episodeLbl = UIHelpers.Label("Ep", panel.transform, "Episode 1", 24, UIHelpers.InkSoft, TextAnchor.UpperRight);
            var ert = ep._episodeLbl.rectTransform;
            ert.anchorMin = new Vector2(0.5f, 0.92f); ert.anchorMax = new Vector2(0.96f, 0.98f);
            ert.offsetMin = Vector2.zero; ert.offsetMax = Vector2.zero;

            ep._titleLbl = UIHelpers.Label("Title", panel.transform, "—", 44, UIHelpers.Ink, TextAnchor.UpperLeft);
            ep._titleLbl.fontStyle = FontStyle.Bold;
            var trt = ep._titleLbl.rectTransform;
            trt.anchorMin = new Vector2(0.04f, 0.82f); trt.anchorMax = new Vector2(0.96f, 0.92f);
            trt.offsetMin = Vector2.zero; trt.offsetMax = Vector2.zero;

            var dialogue = UIHelpers.Box("Dialogue", panel.transform, UIHelpers.Bg2);
            var drt = dialogue.GetComponent<RectTransform>();
            drt.anchorMin = new Vector2(0.04f, 0.55f); drt.anchorMax = new Vector2(0.96f, 0.80f);
            drt.offsetMin = Vector2.zero; drt.offsetMax = Vector2.zero;

            ep._speakerLbl = UIHelpers.Label("Spk", dialogue.transform, "Narrator", 24, UIHelpers.WarmDeep, TextAnchor.UpperLeft);
            ep._speakerLbl.fontStyle = FontStyle.Bold;
            var sprt = ep._speakerLbl.rectTransform;
            sprt.anchorMin = new Vector2(0.03f, 0.78f); sprt.anchorMax = new Vector2(0.97f, 0.98f);
            sprt.offsetMin = Vector2.zero; sprt.offsetMax = Vector2.zero;

            ep._lineLbl = UIHelpers.Label("Ln", dialogue.transform, "—", 26, UIHelpers.Ink, TextAnchor.UpperLeft);
            var lrt = ep._lineLbl.rectTransform;
            lrt.anchorMin = new Vector2(0.03f, 0.02f); lrt.anchorMax = new Vector2(0.97f, 0.78f);
            lrt.offsetMin = Vector2.zero; lrt.offsetMax = Vector2.zero;

            var choicesGO = UIHelpers.Box("Choices", panel.transform, new Color(0,0,0,0));
            var crt2 = choicesGO.GetComponent<RectTransform>();
            crt2.anchorMin = new Vector2(0.04f, 0.05f); crt2.anchorMax = new Vector2(0.96f, 0.55f);
            crt2.offsetMin = Vector2.zero; crt2.offsetMax = Vector2.zero;
            var vlg = choicesGO.AddComponent<VerticalLayoutGroup>();
            vlg.spacing = 12; vlg.childForceExpandWidth = true; vlg.childForceExpandHeight = false;
            vlg.childControlWidth = true; vlg.childControlHeight = true;
            ep._choicesParent = choicesGO.transform;

            ep._continueBtn = UIHelpers.SolidButton("Continue", panel.transform, "Continue", UIHelpers.WarmDeep, Color.white);
            var cbrt = ep._continueBtn.GetComponent<RectTransform>();
            cbrt.anchorMin = new Vector2(0.04f, 0.05f); cbrt.anchorMax = new Vector2(0.96f, 0.16f);
            cbrt.offsetMin = Vector2.zero; cbrt.offsetMax = Vector2.zero;
            ep._continueLbl = ep._continueBtn.GetComponentInChildren<Text>();
            ep._continueBtn.gameObject.SetActive(false);
            ep._continueBtn.onClick.AddListener(ep.OnContinueClicked);

            return ep;
        }

        public void BeginEpisode(Episode ep)
        {
            _currentEp = ep;
            GameManager.I.Dialogue.Begin(ep);
            _visibleLines = GameManager.I.Adaptive.FilterLines(ep.lines);
            _lineIdx = 0;
            _chapterLbl.text = $"Chapter {ep.chapter}";
            _episodeLbl.text = $"Episode {ep.episode}";
            _titleLbl.text = ep.title;
            _charEmoji.text = string.IsNullOrEmpty(ep.charEmoji) ? "🧒" : ep.charEmoji;

            _bg.color = BgColorFor(ep.bg);
            _animator.SetMood(MoodFor(ep.mood));

            ClearChoices();
            _continueBtn.gameObject.SetActive(false);
            ShowNextLine();
        }

        void ShowNextLine()
        {
            if (_lineIdx >= _visibleLines.Count) { ShowChoices(); return; }
            var ln = _visibleLines[_lineIdx++];
            _speakerLbl.text = ln.speaker;
            _lineLbl.text = ln.text;

            if (_lineIdx < _visibleLines.Count)
            {
                ClearChoices();
                _continueBtn.gameObject.SetActive(true);
                _continueLbl.text = "Continue";
            }
            else
            {
                _continueBtn.gameObject.SetActive(false);
                ShowChoices();
            }
        }

        void OnContinueClicked()
        {
            // Either advancing through lines, or finishing an episode
            if (_currentEp != null && _lineIdx < _visibleLines.Count)
            {
                ShowNextLine();
            }
            else
            {
                FindFirstObjectByType<UIRoot>().ShowHome();
            }
        }

        void ShowChoices()
        {
            ClearChoices();
            var choices = _currentEp.choices;
            if (choices == null || choices.Count == 0)
            {
                _continueBtn.gameObject.SetActive(true);
                _continueLbl.text = "Continue";
                return;
            }
            foreach (var c in choices)
            {
                var btn = UIHelpers.SolidButton("Choice", _choicesParent, c.text, UIHelpers.Bg2, UIHelpers.Ink);
                var le = btn.gameObject.AddComponent<LayoutElement>(); le.preferredHeight = 110;
                var local = c;
                btn.onClick.AddListener(() => OnChoiceSelected(local));
            }
        }

        void OnChoiceSelected(ChoiceData c)
        {
            var gm = GameManager.I;
            int epNum = _currentEp.episode;
            string epTitle = _currentEp.title;
            gm.Dialogue.Choose(c); // applies effects, flags, memory, retainer bump
            gm.AdvanceAfterEpisode();
            gm.AppendLog($"Ep {epNum} \"{epTitle}\" -> {c.text}");

            ClearChoices();
            _speakerLbl.text = "Reflection";
            _lineLbl.text = SummarizeEffects(c) + (_currentEp.endsChapter ? "\n\nChapter 1 complete. Memories sealed." : "");
            _continueBtn.gameObject.SetActive(true);
            _continueLbl.text = _currentEp.endsChapter ? "Finish Chapter →" : "Back Home";
            _currentEp = null; _visibleLines = null;
        }

        string SummarizeEffects(ChoiceData c)
        {
            if (c == null || c.effects == null || c.effects.Count == 0) return "You let the moment land.";
            var parts = new List<string>();
            foreach (var e in c.effects)
            {
                var def = DataLoader.Stats.Find(s => s.key == e.key);
                if (def == null) continue;
                parts.Add($"{(e.delta > 0 ? "+" : "")}{e.delta:0.#} {def.label}");
            }
            return string.Join(" · ", parts);
        }

        void ClearChoices()
        {
            for (int i = _choicesParent.childCount - 1; i >= 0; i--)
                Destroy(_choicesParent.GetChild(i).gameObject);
        }

        Color BgColorFor(string bg)
        {
            return bg switch
            {
                "rain" => new Color(0.42f, 0.49f, 0.58f),
                "evening" => new Color(0.78f, 0.38f, 0.16f),
                "night" => new Color(0.06f, 0.09f, 0.19f),
                "festival" => new Color(0.55f, 0.23f, 0.49f),
                "classroom" => new Color(0.86f, 0.91f, 0.69f),
                "village" => new Color(0.78f, 0.90f, 0.69f),
                _ => UIHelpers.Warm,
            };
        }

        KingsChoiceAnimator.Mood MoodFor(string mood)
        {
            return mood switch
            {
                "stressed" => KingsChoiceAnimator.Mood.Stressed,
                "happy" => KingsChoiceAnimator.Mood.Happy,
                _ => KingsChoiceAnimator.Mood.Neutral,
            };
        }
    }
}
