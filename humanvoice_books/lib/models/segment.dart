import 'package:equatable/equatable.dart';

/// One acted line in the audiobook. The Director (LLM) emits a sequence
/// of these; the Enactor (Kokoro TTS) renders each one to PCM and the
/// muxer concatenates them per chapter.
class Segment extends Equatable {
  final String text;
  final String voiceId; // e.g. af_bella, am_adam, af_nicole
  final String emotion; // neutral | whisper | shout | sad | excited
  final double speed; // Kokoro speed multiplier, 1.0 default

  const Segment({
    required this.text,
    required this.voiceId,
    this.emotion = 'neutral',
    this.speed = 1.0,
  });

  factory Segment.fromJson(Map<String, dynamic> j) => Segment(
        text: (j['text'] ?? '').toString(),
        voiceId: (j['voice_id'] ?? 'af_bella').toString(),
        emotion: (j['emotion'] ?? 'neutral').toString(),
        speed: (j['speed'] is num) ? (j['speed'] as num).toDouble() : 1.0,
      );

  Map<String, dynamic> toJson() => {
        'text': text,
        'voice_id': voiceId,
        'emotion': emotion,
        'speed': speed,
      };

  @override
  List<Object?> get props => [text, voiceId, emotion, speed];
}

class Chapter extends Equatable {
  final int index;
  final String title;
  final List<Segment> segments;

  const Chapter({required this.index, required this.title, required this.segments});

  @override
  List<Object?> get props => [index, title, segments];
}
