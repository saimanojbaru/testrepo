using UnityEngine;
using UnityEngine.UI;
using CorporateDragon.Core;

namespace CorporateDragon.UI
{
    public class AlbumScreen : MonoBehaviour
    {
        Transform _gridParent;

        public static AlbumScreen Build(Transform parent)
        {
            var go = UIHelpers.Panel("AlbumScreen", parent, UIHelpers.Bg);
            var s = go.AddComponent<AlbumScreen>();

            var top = UIHelpers.Box("Top", go.transform, new Color(0,0,0,0));
            var trt = top.GetComponent<RectTransform>();
            trt.anchorMin = new Vector2(0, 0.94f); trt.anchorMax = new Vector2(1, 1f);
            trt.offsetMin = Vector2.zero; trt.offsetMax = Vector2.zero;

            var back = UIHelpers.SolidButton("Back", top.transform, "← Back", UIHelpers.Card, UIHelpers.Ink);
            var bk = back.GetComponent<RectTransform>();
            bk.anchorMin = new Vector2(0.03f, 0.2f); bk.anchorMax = new Vector2(0.22f, 0.8f);
            bk.offsetMin = Vector2.zero; bk.offsetMax = Vector2.zero;
            back.onClick.AddListener(() => FindFirstObjectByType<UIRoot>().ShowHome());

            var title = UIHelpers.Label("T", top.transform, "Memory Album", 42, UIHelpers.Ink, TextAnchor.MiddleCenter);
            var trt2 = title.rectTransform;
            trt2.anchorMin = new Vector2(0.22f, 0); trt2.anchorMax = new Vector2(0.78f, 1f);
            trt2.offsetMin = Vector2.zero; trt2.offsetMax = Vector2.zero;
            title.fontStyle = FontStyle.Bold;

            // Scroll
            var scrollGO = UIHelpers.NewObj("Scroll", go.transform);
            var scrt = scrollGO.GetComponent<RectTransform>();
            scrt.anchorMin = new Vector2(0, 0); scrt.anchorMax = new Vector2(1, 0.94f);
            scrt.offsetMin = Vector2.zero; scrt.offsetMax = Vector2.zero;
            var scroll = scrollGO.AddComponent<ScrollRect>();
            scroll.horizontal = false; scroll.vertical = true;

            var viewportGO = UIHelpers.Box("V", scrollGO.transform, new Color(0,0,0,0));
            var vrt = viewportGO.GetComponent<RectTransform>();
            UIHelpers.SetAnchor(vrt, Vector2.zero, Vector2.one);
            viewportGO.AddComponent<Mask>().showMaskGraphic = false;
            scroll.viewport = vrt;

            var contentGO = UIHelpers.NewObj("Content", viewportGO.transform);
            var crt = contentGO.GetComponent<RectTransform>();
            crt.anchorMin = new Vector2(0, 1); crt.anchorMax = new Vector2(1, 1); crt.pivot = new Vector2(0.5f, 1f);
            crt.sizeDelta = new Vector2(0, 1200);
            var grid = contentGO.AddComponent<GridLayoutGroup>();
            grid.cellSize = new Vector2(480, 320);
            grid.spacing = new Vector2(24, 24);
            grid.padding = new RectOffset(40, 40, 40, 40);
            grid.constraint = GridLayoutGroup.Constraint.FixedColumnCount;
            grid.constraintCount = 2;
            var csf = contentGO.AddComponent<ContentSizeFitter>();
            csf.verticalFit = ContentSizeFitter.FitMode.PreferredSize;
            scroll.content = crt;
            s._gridParent = contentGO.transform;

            return s;
        }

        public void Refresh()
        {
            for (int i = _gridParent.childCount - 1; i >= 0; i--)
                Destroy(_gridParent.GetChild(i).gameObject);

            foreach (var c in DataLoader.MemoryCards)
            {
                bool unlocked = GameManager.I.Album.IsUnlocked(c.id);
                var card = UIHelpers.Box("C", _gridParent, unlocked ? UIHelpers.Card : new Color(0.85f, 0.78f, 0.66f));
                var emoji = UIHelpers.Label("E", card.transform, unlocked ? c.emoji : "🔒", 90, UIHelpers.Ink, TextAnchor.MiddleCenter);
                var ert = emoji.rectTransform;
                ert.anchorMin = new Vector2(0, 0.5f); ert.anchorMax = new Vector2(1, 1f);
                ert.offsetMin = Vector2.zero; ert.offsetMax = Vector2.zero;
                var name = UIHelpers.Label("N", card.transform, unlocked ? c.name : "???", 28, UIHelpers.Ink, TextAnchor.MiddleCenter);
                name.fontStyle = FontStyle.Bold;
                var nrt = name.rectTransform;
                nrt.anchorMin = new Vector2(0.05f, 0.30f); nrt.anchorMax = new Vector2(0.95f, 0.5f);
                nrt.offsetMin = Vector2.zero; nrt.offsetMax = Vector2.zero;
                var desc = UIHelpers.Label("D", card.transform, unlocked ? c.desc : "A memory not yet made.", 20, UIHelpers.InkSoft, TextAnchor.UpperCenter);
                var drt = desc.rectTransform;
                drt.anchorMin = new Vector2(0.05f, 0.04f); drt.anchorMax = new Vector2(0.95f, 0.30f);
                drt.offsetMin = Vector2.zero; drt.offsetMax = Vector2.zero;
            }
        }
    }
}
