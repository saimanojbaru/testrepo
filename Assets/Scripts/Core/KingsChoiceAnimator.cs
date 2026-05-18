using UnityEngine;

namespace CorporateDragon.Core
{
    /// Transform-based "breathing" + mood lean.
    /// Designed so Spine 2D / Live2D can be plugged in later by replacing the
    /// transform pulses with bone manipulations and the mood lean with animation tracks.
    public class KingsChoiceAnimator : MonoBehaviour
    {
        public enum Mood { Neutral, Stressed, Happy }

        [SerializeField] float breathSpeed = 1.8f;
        [SerializeField] float breathAmount = 0.025f;
        [SerializeField] float moodLerpSpeed = 3f;

        Mood _mood = Mood.Neutral;
        Vector3 _baseScale;
        Vector3 _basePos;
        float _phase;
        float _currentLean;

        void Awake()
        {
            _baseScale = transform.localScale;
            _basePos = transform.localPosition;
        }

        public void SetMood(Mood m) { _mood = m; }

        void Update()
        {
            _phase += Time.deltaTime * breathSpeed;
            float breath = Mathf.Sin(_phase) * breathAmount;

            float targetLean = 0f;
            float yBob = 0f;
            float zRot = 0f;
            switch (_mood)
            {
                case Mood.Stressed: targetLean = -3f; yBob = -4f; break;
                case Mood.Happy:    targetLean = 0f;  yBob = Mathf.Sin(_phase * 2.5f) * 6f; break;
            }
            _currentLean = Mathf.Lerp(_currentLean, targetLean, Time.deltaTime * moodLerpSpeed);

            transform.localScale = new Vector3(_baseScale.x, _baseScale.y * (1f + breath), _baseScale.z);
            transform.localPosition = _basePos + new Vector3(0f, yBob, 0f);
            transform.localRotation = Quaternion.Euler(0f, 0f, _currentLean + zRot);
        }
    }
}
