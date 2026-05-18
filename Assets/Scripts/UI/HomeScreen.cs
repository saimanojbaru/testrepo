using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
using CorporateDragon.Core;
using CorporateDragon.Data;

namespace CorporateDragon.UI
{
    public class HomeScreen : MonoBehaviour
    {
        Text _ageTag, _energyNum, _storyStatus;
        Image _energyFill;
        Transform _statsParent, _retainerParent, _festivalParent, _actionParent, _logParent;
        Button _playEp, _album;

        public static HomeScreen Build(Transform parent)
        {
            var go = UIHelpers.Panel("HomeScreen", parent, UIHelpers.Bg);
            var hs = go.AddComponent<HomeScreen>();

            var scrollGO = UIHelpers.NewObj("Scroll", go.transform);
            var scrt = scrollGO.GetComponent<RectTransform>();
            UIHelpers.SetAnchor(scrt, Vector2.zero, Vector2.one);
            var scroll = scrollGO.AddComponent<ScrollRect>();
            scroll.horizontal = false;
            scroll.vertical = true;
            scroll.movementType = ScrollRect.MovementType.Elastic;

            var viewportGO = UIHelpers.Box("Viewport", scrollGO.transform, new Color(0,0,0,0));
            var viewRt = viewportGO.GetComponent<RectTransform>();
            UIHelpers.SetAnchor(viewRt, Vector2.zero, Vector2.one);
            viewportGO.AddComponent<Mask>().showMaskGraphic = false;
            scroll.viewport = viewRt;

            var contentGO = UIHelpers.NewObj("Content", viewportGO.transform);
            var crt = contentGO.GetComponent<RectTransform>();
            crt.anchorMin = new Vector2(0, 1);
            crt.anchorMax = new Vector2(1, 1);
            crt.pivot = new Vector2(0.5f, 1f);
            crt.sizeDelta = new Vector2(0, 2400);
            var vlg = contentGO.AddComponent<VerticalLayoutGroup>();
            vlg.padding = new RectOffset(40, 40, 40, 40);
            vlg.spacing = 28;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            vlg.childControlWidth = true;
            vlg.childControlHeight = true;
            var csf = contentGO.AddComponent<ContentSizeFitter>();
            csf.verticalFit = ContentSizeFitter.FitMode.PreferredSize;
            scroll.content = crt;

            var content = contentGO.transform;

            // Top bar
            var top = UIHelpers.Box("TopBar", content, new Color(0,0,0,0));
            var topLE = top.AddComponent<LayoutElement>(); topLE.preferredHeight = 130;
            var topRT = top.GetComponent<RectTransform>();

            var ageBox = UIHelpers.Box("Age", top.transform, UIHelpers.Ink);
            var ageRT = ageBox.GetComponent<RectTransform>();
            ageRT.anchorMin = new Vector2(0, 0.2f); ageRT.anchorMax = new Vector2(0.25f, 0.8f);
            ageRT.offsetMin = Vector2.zero; ageRT.offsetMax = Vector2.zero;
            hs._ageTag = UIHelpers.Label("AgeText", ageBox.transform, "Age 5", 36, UIHelpers.Bg2, TextAnchor.MiddleCenter);
            UIHelpers.SetAnchor(hs._ageTag.rectTransform, Vector2.zero, Vector2.one);

            var energyLbl = UIHelpers.Label("EnergyLbl", top.transform, "LIFE ENERGY", 28, UIHelpers.InkSoft, TextAnchor.LowerLeft);
            var elr = energyLbl.rectTransform;
            elr.anchorMin = new Vector2(0.28f, 0.7f); elr.anchorMax = new Vector2(1f, 1f);
            elr.offsetMin = Vector2.zero; elr.offsetMax = Vector2.zero;

            var trackGO = UIHelpers.FilledBar("EnergyTrack", top.transform, UIHelpers.WarmDeep, new Color(0,0,0,0.1f), out var fill);
            var trackRT = trackGO.GetComponent<RectTransform>();
            trackRT.anchorMin = new Vector2(0.28f, 0.4f); trackRT.anchorMax = new Vector2(0.85f, 0.6f);
            trackRT.offsetMin = Vector2.zero; trackRT.offsetMax = Vector2.zero;
            hs._energyFill = fill;
            hs._energyNum = UIHelpers.Label("EnergyNum", top.transform, "100/100", 28, UIHelpers.InkSoft, TextAnchor.MiddleRight);
            var enr = hs._energyNum.rectTransform;
            enr.anchorMin = new Vector2(0.85f, 0.3f); enr.anchorMax = new Vector2(1f, 0.7f);
            enr.offsetMin = Vector2.zero; enr.offsetMax = Vector2.zero;

            // Stats card
            hs._statsParent = MakeCardWithHeader(content, "CORE STATS", out _);

            // Retainers card
            hs._retainerParent = MakeCardWithHeader(content, "FAMILY · COURT GALLERY", out _);

            // Festivals card
            hs._festivalParent = MakeCardWithHeader(content, "UPCOMING CELEBRATIONS", out _);

            // Actions card
            hs._actionParent = MakeCardWithHeader(content, "DAILY ACTIVITIES", out _);

            // Story card
            var storyCard = MakeCardWithHeader(content, "STORY", out _);
            hs._storyStatus = UIHelpers.Label("Status", storyCard, "—", 28, UIHelpers.InkSoft, TextAnchor.UpperLeft);
            var sst = hs._storyStatus.gameObject.AddComponent<LayoutElement>();
            sst.preferredHeight = 60;

            hs._playEp = UIHelpers.SolidButton("Play", storyCard, "Play Next Episode", UIHelpers.WarmDeep, Color.white);
            var pe = hs._playEp.gameObject.AddComponent<LayoutElement>();
            pe.preferredHeight = 110;
            hs._playEp.onClick.AddListener(() =>
            {
                var ep = GameManager.I.CurrentEpisode;
                if (ep == null) return;
                var ui = FindFirstObjectByType<UIRoot>();
                ui.Episode.BeginEpisode(ep);
                ui.ShowEpisode();
            });

            hs._album = UIHelpers.SolidButton("Album", storyCard, "Memory Album", UIHelpers.Card, UIHelpers.Ink);
            var ab = hs._album.gameObject.AddComponent<LayoutElement>();
            ab.preferredHeight = 100;
            hs._album.onClick.AddListener(() => FindFirstObjectByType<UIRoot>().ShowAlbum());

            // Log
            var logCard = MakeCardWithHeader(content, "RECENT REFLECTIONS", out _);
            hs._logParent = logCard;

            return hs;
        }

        static Transform MakeCardWithHeader(Transform parent, string headerText, out GameObject card)
        {
            var go = UIHelpers.Box("Card", parent, UIHelpers.Card);
            card = go;
            var vlg = go.AddComponent<VerticalLayoutGroup>();
            vlg.padding = new RectOffset(28, 28, 24, 24);
            vlg.spacing = 16;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            vlg.childControlWidth = true;
            vlg.childControlHeight = true;
            var csf = go.AddComponent<ContentSizeFitter>();
            csf.verticalFit = ContentSizeFitter.FitMode.PreferredSize;

            var head = UIHelpers.Label("Header", go.transform, headerText, 28, UIHelpers.InkSoft, TextAnchor.UpperLeft);
            head.fontStyle = FontStyle.Bold;
            var hle = head.gameObject.AddComponent<LayoutElement>();
            hle.preferredHeight = 40;
            return go.transform;
        }

        public void Refresh()
        {
            var gm = GameManager.I;
            gm.Energy.Tick();

            var ep = gm.CurrentEpisode;
            _ageTag.text = ep != null ? $"Age {ep.age}" : "Resolution";

            _energyNum.text = $"{Mathf.FloorToInt(gm.Energy.Energy)}/{Mathf.FloorToInt(gm.Energy.MaxEnergy)}";
            UIHelpers.SetFill(_energyFill, gm.Energy.Energy / gm.Energy.MaxEnergy);

            BuildStats();
            BuildRetainers();
            BuildFestivals();
            BuildActions();
            BuildStory();
            BuildLog();

            gm.SaveGame();
        }

        void ClearChildren(Transform parent, int skipFirst = 1)
        {
            for (int i = parent.childCount - 1; i >= skipFirst; i--)
                Destroy(parent.GetChild(i).gameObject);
        }

        void BuildStats()
        {
            ClearChildren(_statsParent);
            foreach (var s in DataLoader.Stats)
            {
                var row = UIHelpers.Box("StatRow", _statsParent, new Color(0, 0, 0, 0));
                var le = row.AddComponent<LayoutElement>(); le.preferredHeight = 50;
                var label = UIHelpers.Label("Lbl", row.transform, $"{s.label}  {Mathf.FloorToInt(GameManager.I.Stats.Get(s.key))}", 26, UIHelpers.Ink, TextAnchor.MiddleLeft);
                var lrt = label.rectTransform;
                lrt.anchorMin = new Vector2(0, 0.5f); lrt.anchorMax = new Vector2(0.55f, 1f);
                lrt.offsetMin = Vector2.zero; lrt.offsetMax = Vector2.zero;
                var bar = UIHelpers.FilledBar("Bar", row.transform, UIHelpers.Leaf, new Color(0, 0, 0, 0.08f), out var fill);
                var brt = bar.GetComponent<RectTransform>();
                brt.anchorMin = new Vector2(0, 0.15f); brt.anchorMax = new Vector2(1f, 0.42f);
                brt.offsetMin = Vector2.zero; brt.offsetMax = Vector2.zero;
                UIHelpers.SetFill(fill, GameManager.I.Stats.Get(s.key) / 100f);
            }
        }

        void BuildRetainers()
        {
            ClearChildren(_retainerParent);
            foreach (var r in DataLoader.Retainers)
            {
                var row = UIHelpers.Box("R", _retainerParent, new Color(0, 0, 0, 0));
                var le = row.AddComponent<LayoutElement>(); le.preferredHeight = 90;
                var av = UIHelpers.Box("Avatar", row.transform, UIHelpers.Warm);
                var ar = av.GetComponent<RectTransform>();
                ar.anchorMin = new Vector2(0, 0.1f); ar.anchorMax = new Vector2(0.15f, 0.9f);
                ar.offsetMin = Vector2.zero; ar.offsetMax = Vector2.zero;
                var avLbl = UIHelpers.Label("E", av.transform, r.emoji, 50, UIHelpers.Ink, TextAnchor.MiddleCenter);
                UIHelpers.SetAnchor(avLbl.rectTransform, Vector2.zero, Vector2.one);

                int aff = GameManager.I.Retainers.GetAffinity(r.id);
                var name = UIHelpers.Label("N", row.transform, r.name, 28, UIHelpers.Ink, TextAnchor.LowerLeft);
                var nrt = name.rectTransform;
                nrt.anchorMin = new Vector2(0.18f, 0.55f); nrt.anchorMax = new Vector2(1f, 0.9f);
                nrt.offsetMin = Vector2.zero; nrt.offsetMax = Vector2.zero;
                name.fontStyle = FontStyle.Bold;

                var bar = UIHelpers.FilledBar("Bar", row.transform, UIHelpers.Rose, new Color(0, 0, 0, 0.08f), out var fill);
                var brt = bar.GetComponent<RectTransform>();
                brt.anchorMin = new Vector2(0.18f, 0.32f); brt.anchorMax = new Vector2(0.85f, 0.45f);
                brt.offsetMin = Vector2.zero; brt.offsetMax = Vector2.zero;
                UIHelpers.SetFill(fill, aff / 10f);

                var lvl = UIHelpers.Label("L", row.transform, $"Affinity {aff}/10", 22, UIHelpers.InkSoft, TextAnchor.UpperLeft);
                var lr = lvl.rectTransform;
                lr.anchorMin = new Vector2(0.18f, 0.10f); lr.anchorMax = new Vector2(1f, 0.30f);
                lr.offsetMin = Vector2.zero; lr.offsetMax = Vector2.zero;
            }
        }

        void BuildFestivals()
        {
            ClearChildren(_festivalParent);
            foreach (var u in GameManager.I.Festivals.Upcoming(4))
            {
                var row = UIHelpers.Box("F", _festivalParent, new Color(1f, 0.796f, 0.353f, 0.20f));
                var le = row.AddComponent<LayoutElement>(); le.preferredHeight = 80;
                var name = UIHelpers.Label("N", row.transform, $"{u.festival.emoji}  {u.festival.name}", 30, UIHelpers.Ink, TextAnchor.MiddleLeft);
                var nrt = name.rectTransform;
                nrt.anchorMin = new Vector2(0.03f, 0); nrt.anchorMax = new Vector2(0.7f, 1f);
                nrt.offsetMin = Vector2.zero; nrt.offsetMax = Vector2.zero;
                var when = UIHelpers.Label("W", row.transform, Core.FestivalManager.WhenLabel(u.daysAway, u.date), 26, UIHelpers.InkSoft, TextAnchor.MiddleRight);
                var wrt = when.rectTransform;
                wrt.anchorMin = new Vector2(0.7f, 0); wrt.anchorMax = new Vector2(0.97f, 1f);
                wrt.offsetMin = Vector2.zero; wrt.offsetMax = Vector2.zero;
            }
        }

        void BuildActions()
        {
            ClearChildren(_actionParent);
            foreach (var a in DataLoader.DailyActions)
            {
                var canSpend = GameManager.I.Energy.Energy >= a.cost;
                var btn = UIHelpers.SolidButton("A", _actionParent, "", UIHelpers.Bg2, UIHelpers.Ink);
                var le = btn.gameObject.AddComponent<LayoutElement>(); le.preferredHeight = 130;
                var lbl = btn.GetComponentInChildren<Text>();
                lbl.text = $"{a.name}\n<color=#6B4F2A>Energy −{a.cost}</color>\n<color=#6FA174>{a.flavor}</color>";
                lbl.alignment = TextAnchor.MiddleLeft;
                lbl.supportRichText = true;
                lbl.fontSize = 24;
                btn.interactable = canSpend;
                var local = a;
                btn.onClick.AddListener(() => DoAction(local));
            }
        }

        void BuildStory()
        {
            var ep = GameManager.I.CurrentEpisode;
            if (ep == null)
            {
                _storyStatus.text = "Chapter 1 complete. More chapters in next release.";
                _playEp.interactable = false;
                _playEp.GetComponentInChildren<Text>().text = "✓ Chapter 1 Complete";
            }
            else
            {
                _storyStatus.text = $"Chapter {ep.chapter} · Episode {ep.episode} · \"{ep.title}\"";
                _playEp.interactable = true;
                _playEp.GetComponentInChildren<Text>().text = $"Play: {ep.title}";
            }
        }

        void BuildLog()
        {
            ClearChildren(_logParent);
            if (GameManager.I.Log.Count == 0)
            {
                var l = UIHelpers.Label("E", _logParent, "No reflections yet. Make a choice and watch the world remember.", 22, UIHelpers.InkSoft, TextAnchor.MiddleLeft);
                var le = l.gameObject.AddComponent<LayoutElement>(); le.preferredHeight = 50;
                return;
            }
            int shown = 0;
            foreach (var e in GameManager.I.Log)
            {
                if (shown++ > 10) break;
                var row = UIHelpers.Box("L", _logParent, new Color(1f, 0.796f, 0.353f, 0.10f));
                var le = row.AddComponent<LayoutElement>(); le.preferredHeight = 60;
                var t = UIHelpers.Label("T", row.transform, e, 22, UIHelpers.InkSoft, TextAnchor.MiddleLeft);
                var rt = t.rectTransform;
                UIHelpers.SetAnchor(rt, new Vector2(0.03f, 0), new Vector2(0.97f, 1));
            }
        }

        void DoAction(DailyAction a)
        {
            var gm = GameManager.I;
            if (!gm.Energy.TrySpend(a.cost)) return;
            gm.Stats.Apply(a.effects);
            if (!string.IsNullOrEmpty(a.retainer)) gm.Retainers.Bump(a.retainer, 1);
            gm.AppendLog($"{a.name} — {a.flavor}");
            Refresh();
        }
    }
}
