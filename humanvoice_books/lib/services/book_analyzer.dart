import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:isolate';

import 'package:epubx/epubx.dart' as epub;
import 'package:sherpa_onnx/sherpa_onnx.dart' as so;

import '../models/segment.dart';
import 'model_service.dart';

/// Director-side message passed into the analyzer isolate.
class _AnalyzeRequest {
  final SendPort reply;
  final String epubPath;
  final String qwenModelDir;
  const _AnalyzeRequest(this.reply, this.epubPath, this.qwenModelDir);
}

/// Output messages the analyzer pushes back to the UI isolate.
sealed class AnalyzerEvent {}

class AnalyzerProgress extends AnalyzerEvent {
  final int chapterIndex;
  final int chapterCount;
  final String chapterTitle;
  AnalyzerProgress(this.chapterIndex, this.chapterCount, this.chapterTitle);
}

class AnalyzerChapter extends AnalyzerEvent {
  final Chapter chapter;
  AnalyzerChapter(this.chapter);
}

class AnalyzerDone extends AnalyzerEvent {}

class AnalyzerError extends AnalyzerEvent {
  final String message;
  AnalyzerError(this.message);
}

/// Director: parses the EPUB and asks Qwen2.5-1.5B to assign voice + emotion
/// to each paragraph, streaming the results back as [Chapter] objects.
///
/// Runs in a separate Isolate so the UI thread stays at 60fps even while
/// the LLM crunches through a long book.
class BookAnalyzer {
  /// Returns a broadcast stream of analyzer events. The underlying isolate
  /// is spawned on first listen and shut down when the stream is cancelled.
  static Stream<AnalyzerEvent> analyze(String epubPath) async* {
    final qwen = await ModelService.instance.qwenDir();
    final rx = ReceivePort();
    final iso = await Isolate.spawn<_AnalyzeRequest>(
      _entry,
      _AnalyzeRequest(rx.sendPort, epubPath, qwen.path),
      errorsAreFatal: false,
    );
    try {
      await for (final msg in rx) {
        if (msg is AnalyzerEvent) {
          yield msg;
          if (msg is AnalyzerDone || msg is AnalyzerError) break;
        }
      }
    } finally {
      iso.kill(priority: Isolate.immediate);
      rx.close();
    }
  }

  // ===== isolate entry point =====
  static Future<void> _entry(_AnalyzeRequest req) async {
    final tx = req.reply;
    try {
      // 1. Parse the EPUB.
      final bytes = await File(req.epubPath).readAsBytes();
      final book = await epub.EpubReader.readBook(bytes);
      final rawChapters = book.Chapters ?? <epub.EpubChapter>[];
      final flat = _flattenChapters(rawChapters);

      // 2. Boot the on-device LLM (Qwen2.5-1.5B Q4_K_M via sherpa-onnx).
      final llm = _bootLlm(req.qwenModelDir);

      // 3. Walk chapters; for each, stream paragraphs to the LLM.
      for (var i = 0; i < flat.length; i++) {
        final c = flat[i];
        final title = (c.Title ?? 'Chapter ${i + 1}').trim();
        tx.send(AnalyzerProgress(i, flat.length, title));

        final paragraphs = _splitParagraphs(_stripHtml(c.HtmlContent ?? ''));
        final segments = <Segment>[];
        var tokensSinceFlush = 0;

        for (final p in paragraphs) {
          if (p.trim().isEmpty) continue;
          final out = _direct(llm, p);
          segments.addAll(out);

          // Approximate token count: ~1 token per 4 chars. Flush every 2K tokens.
          tokensSinceFlush += (p.length / 4).ceil();
          if (tokensSinceFlush >= 2000) {
            // Reset KV cache to keep RAM bounded on low-end devices.
            llm.reset();
            tokensSinceFlush = 0;
          }
        }

        tx.send(AnalyzerChapter(Chapter(
          index: i,
          title: title,
          segments: segments,
        )));
      }

      llm.free();
      tx.send(AnalyzerDone());
    } catch (e, st) {
      tx.send(AnalyzerError('$e\n$st'));
    }
  }

  static List<epub.EpubChapter> _flattenChapters(List<epub.EpubChapter> roots) {
    final out = <epub.EpubChapter>[];
    void walk(epub.EpubChapter c) {
      out.add(c);
      for (final s in c.SubChapters ?? const <epub.EpubChapter>[]) {
        walk(s);
      }
    }
    for (final c in roots) {
      walk(c);
    }
    return out;
  }

  static String _stripHtml(String html) {
    // Lightweight HTML stripper — full html parser would bloat the APK.
    final noTags = html.replaceAll(RegExp(r'<[^>]+>'), ' ');
    return noTags
        .replaceAll('&nbsp;', ' ')
        .replaceAll('&amp;', '&')
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
  }

  static List<String> _splitParagraphs(String text) {
    // Split on double newlines first (paragraph breaks), then on sentence
    // boundaries inside long paragraphs so each LLM prompt stays small.
    final paras = text.split(RegExp(r'\n{2,}|\r\n\r\n'));
    final out = <String>[];
    for (final p in paras) {
      final t = p.trim();
      if (t.isEmpty) continue;
      if (t.length <= 600) {
        out.add(t);
      } else {
        out.addAll(_splitSentences(t));
      }
    }
    return out;
  }

  static List<String> _splitSentences(String text) {
    final out = <String>[];
    final buf = StringBuffer();
    for (var i = 0; i < text.length; i++) {
      buf.write(text[i]);
      final c = text[i];
      if ((c == '.' || c == '!' || c == '?') && buf.length > 280) {
        out.add(buf.toString().trim());
        buf.clear();
      }
    }
    if (buf.isNotEmpty) out.add(buf.toString().trim());
    return out;
  }

  // ====== LLM glue ======

  static so.OfflineLM _bootLlm(String dir) {
    // sherpa-onnx supports llama.cpp-style GGUF via OfflineLM.
    final cfg = so.OfflineLMConfig(
      model: '$dir/qwen2.5-1.5b-instruct-q4_k_m.gguf',
      numThreads: 2,
      provider: 'cpu',
      // Conservative context to keep peak RAM under ~700MB on a 4GB device.
      contextLength: 2048,
    );
    return so.OfflineLM(config: cfg);
  }

  static const _systemPrompt = '''
You are the Director of an audiobook. For each paragraph, decide which lines
are narration vs. dialogue, who is speaking, and the emotion. Output ONLY a
JSON array of objects with this exact shape:
[{"text": "...", "voice_id": "af_bella" | "am_adam" | "af_nicole" | "am_michael",
  "emotion": "neutral" | "whisper" | "shout" | "sad" | "excited", "speed": 1.0}]
Rules:
- Narration -> af_bella (calm female) by default.
- Male speaker dialogue -> am_adam or am_michael.
- Female speaker dialogue -> af_nicole.
- Whispers, asides -> emotion "whisper", speed 0.9.
- Yelling, exclamations -> emotion "shout", speed 1.05.
- Do NOT include any prose outside the JSON array.
''';

  static List<Segment> _direct(so.OfflineLM llm, String paragraph) {
    final prompt = '<|im_start|>system\n$_systemPrompt<|im_end|>\n'
        '<|im_start|>user\n$paragraph<|im_end|>\n'
        '<|im_start|>assistant\n';
    final out = llm.generate(prompt: prompt, maxTokens: 512, temperature: 0.2);
    return _parseDirectorOutput(out, fallback: paragraph);
  }

  static List<Segment> _parseDirectorOutput(String raw, {required String fallback}) {
    // Pull the first JSON array out of the LLM response. The model occasionally
    // wraps it in code fences or trailing prose despite the system prompt.
    final start = raw.indexOf('[');
    final end = raw.lastIndexOf(']');
    if (start < 0 || end <= start) {
      return [Segment(text: fallback, voiceId: 'af_bella')];
    }
    try {
      final list = jsonDecode(raw.substring(start, end + 1)) as List;
      final segs = <Segment>[];
      for (final item in list) {
        if (item is Map<String, dynamic>) {
          final s = Segment.fromJson(item);
          if (s.text.trim().isNotEmpty) segs.add(s);
        }
      }
      if (segs.isEmpty) {
        return [Segment(text: fallback, voiceId: 'af_bella')];
      }
      return segs;
    } catch (_) {
      return [Segment(text: fallback, voiceId: 'af_bella')];
    }
  }
}
