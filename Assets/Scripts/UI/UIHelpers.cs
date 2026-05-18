using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace CorporateDragon.UI
{
    public static class UIHelpers
    {
        public static readonly Color Bg = new Color(0.957f, 0.886f, 0.784f);          // #F4E2C8
        public static readonly Color Bg2 = new Color(1f, 0.949f, 0.851f);             // #FFF2D9
        public static readonly Color Ink = new Color(0.231f, 0.165f, 0.078f);         // #3B2A14
        public static readonly Color InkSoft = new Color(0.420f, 0.310f, 0.165f);     // #6B4F2A
        public static readonly Color Warm = new Color(1f, 0.796f, 0.353f);            // #FFCB5A
        public static readonly Color WarmDeep = new Color(0.910f, 0.608f, 0.169f);    // #E89B2B
        public static readonly Color Rose = new Color(0.886f, 0.478f, 0.427f);        // #E27A6D
        public static readonly Color Leaf = new Color(0.435f, 0.631f, 0.455f);        // #6FA174
        public static readonly Color Card = new Color(1f, 0.980f, 0.925f);            // #FFFAEC

        public static Font DefaultFont => Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");

        public static GameObject NewObj(string name, Transform parent)
        {
            var go = new GameObject(name, typeof(RectTransform));
            go.transform.SetParent(parent, false);
            return go;
        }

        public static RectTransform Rect(GameObject go)
        {
            return go.GetComponent<RectTransform>();
        }

        public static GameObject Panel(string name, Transform parent, Color color)
        {
            var go = NewObj(name, parent);
            var img = go.AddComponent<Image>();
            img.color = color;
            var rt = go.GetComponent<RectTransform>();
            rt.anchorMin = Vector2.zero; rt.anchorMax = Vector2.one;
            rt.offsetMin = Vector2.zero; rt.offsetMax = Vector2.zero;
            return go;
        }

        public static GameObject Box(string name, Transform parent, Color color)
        {
            var go = NewObj(name, parent);
            var img = go.AddComponent<Image>();
            img.color = color;
            return go;
        }

        public static Text Label(string name, Transform parent, string text, int size, Color color, TextAnchor anchor = TextAnchor.MiddleLeft)
        {
            var go = NewObj(name, parent);
            var t = go.AddComponent<Text>();
            t.text = text;
            t.font = DefaultFont;
            t.fontSize = size;
            t.color = color;
            t.alignment = anchor;
            t.horizontalOverflow = HorizontalWrapMode.Wrap;
            t.verticalOverflow = VerticalWrapMode.Overflow;
            t.raycastTarget = false;
            return t;
        }

        public static Button SolidButton(string name, Transform parent, string label, Color bg, Color fg)
        {
            var go = NewObj(name, parent);
            var img = go.AddComponent<Image>();
            img.color = bg;
            var btn = go.AddComponent<Button>();
            var colors = btn.colors;
            colors.normalColor = Color.white;
            colors.highlightedColor = new Color(1f, 1f, 1f, 0.9f);
            colors.pressedColor = new Color(0.9f, 0.9f, 0.9f);
            btn.colors = colors;
            btn.targetGraphic = img;

            var labelObj = Label("Label", go.transform, label, 30, fg, TextAnchor.MiddleCenter);
            var lrt = labelObj.GetComponent<RectTransform>();
            lrt.anchorMin = Vector2.zero; lrt.anchorMax = Vector2.one;
            lrt.offsetMin = new Vector2(12, 8); lrt.offsetMax = new Vector2(-12, -8);
            return btn;
        }

        public static Image FilledBar(string name, Transform parent, Color barColor, Color trackColor, out Image fill)
        {
            var track = Box(name, parent, trackColor).GetComponent<Image>();
            var fillGo = NewObj("Fill", track.transform);
            var fillImg = fillGo.AddComponent<Image>();
            fillImg.color = barColor;
            var rt = fillGo.GetComponent<RectTransform>();
            rt.anchorMin = new Vector2(0, 0);
            rt.anchorMax = new Vector2(1, 1);
            rt.pivot = new Vector2(0, 0.5f);
            rt.offsetMin = Vector2.zero;
            rt.offsetMax = Vector2.zero;
            fill = fillImg;
            return track;
        }

        public static void SetFill(Image fill, float pct)
        {
            pct = Mathf.Clamp01(pct);
            var rt = fill.rectTransform;
            var parent = (RectTransform)rt.parent;
            var w = parent.rect.width;
            rt.anchorMin = new Vector2(0, 0);
            rt.anchorMax = new Vector2(0, 1);
            rt.offsetMin = new Vector2(0, 0);
            rt.offsetMax = new Vector2(w * pct, 0);
        }

        public static void SetAnchor(RectTransform rt, Vector2 min, Vector2 max)
        {
            rt.anchorMin = min;
            rt.anchorMax = max;
            rt.offsetMin = Vector2.zero;
            rt.offsetMax = Vector2.zero;
        }

        public static void EnsureEventSystem()
        {
            if (Object.FindFirstObjectByType<EventSystem>() == null)
            {
                var es = new GameObject("EventSystem", typeof(EventSystem), typeof(StandaloneInputModule));
                Object.DontDestroyOnLoad(es);
            }
        }
    }
}
