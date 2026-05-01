import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:epubx/epubx.dart' as epub;
import 'package:fllama/fllama.dart';
import 'package:fllama/fllama_type.dart';

import '../models/segment.dart';
import 'model_service.dart';

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

/// Director: parses an EPUB and asks Qwen2.5-1.5B-Instruct (Q4_K_M GGUF, via
/// fllama → llama.cpp) to assign a voice + emotion to each paragraph.
///
/// Unlike the Enactor's TTS work, this stays on the root isolate because
/// fllama is platform-channel-based; running it inside a spawned Isolate
/// would require BackgroundIsolateBinaryMessenger setup that isn't worth
/// the complexity here. The actual llama.cpp inference happens off the UI
/// thread anyway via the platform plugin's native thread pool.
class BookAnalyzer {
  /// Approximate per-paragraph token budget. Once we cross this many tokens
  /// since the last reset we release the llama context and create a new one
  /// to keep the KV cache from ballooning on long books.
  static const _kvResetTokens = 2000;

  /// Streams analyzer events as the LLM walks the book.
  static Stream<AnalyzerEvent> analyze(String epubPath) async* {
    final qwen = await ModelService.instance.qwenDir();
    final modelPath = '${qwen.path}/qwen2.5-1.5b-instruct-q4_k_m.gguf';
    if (!await File(modelPath).exists()) {
      yield AnalyzerError('Qwen model missing at $modelPath');
      return;
    }

    final fllama = Fllama.instance();
    if (fllama == null) {
      yield AnalyzerError('fllama plugin failed to initialise');
      return;
    }

    double? ctxId = await _bootContext(fllama, modelPath);
    if (ctxId == null) {
      yield AnalyzerError('initContext returned null');
      return;
    }

    try {
      final bytes = await File(epubPath).readAsBytes();
      final book = await epub.EpubReader.readBook(bytes);
      final flat = _flattenChapters(book.Chapters ?? const <epub.EpubChapter>[]);

      var tokensSinceReset = 0;

      for (var i = 0; i < flat.length; i++) {
        final c = flat[i];
        final title = (c.Title ?? 'Chapter ${i + 1}').trim();
        yield AnalyzerProgress(i, flat.length, title);

        final paragraphs = _splitParagraphs(_stripHtml(c.HtmlContent ?? ''));
        final segments = <Segment>[];

        for (final p in paragraphs) {
          if (p.trim().isEmpty) continue;

          if (tokensSinceReset >= _kvResetTokens) {
            await fllama.releaseContext(ctxId!);
            ctxId = await _bootContext(fllama, modelPath);
            if (ctxId == null) {
              yield AnalyzerError('failed to reboot llama context');
              return;
            }
            tokensSinceReset = 0;
          }

          final out = await _direct(fllama, ctxId!, p);
          segments.addAll(out);
          tokensSinceReset += (p.length / 4).ceil();
        }

        yield AnalyzerChapter(Chapter(
          index: i,
          title: title,
          segments: segments,
        ));
      }
      yield AnalyzerDone();
    } catch (e, st) {
      yield AnalyzerError('$e\n$st');
    } finally {
      if (ctxId != null) {
        await fllama.releaseContext(ctxId);
      }
    }
  }

  static Future<double?> _bootContext(Fllama f, String modelPath) async {
    final m = await f.initContext(
      modelPath,
      nCtx: 2048,
      nBatch: 256,
      nThreads: 2,
      useMmap: true,
      useMlock: false,
    );
    final id = m?['contextId'];
    if (id is num) return id.toDouble();
    if (id is double) return id;
    return null;
  }

  static const _systemPrompt = '''
You are the Director of an audiobook. For each paragraph the user gives you,
return ONLY a JSON array of acted lines. No prose, no code fences.

Each element MUST be:
{"text": "...", "voice_id": "af_bella"|"am_adam"|"af_nicole"|"am_michael",
 "emotion": "neutral"|"whisper"|"shout"|"sad"|"excited", "speed": 1.0}

Rules:
- Narration -> af_bella by default.
- Male dialogue -> am_adam (or am_michael for variety).
- Female dialogue -> af_nicole.
- Whispers / asides -> emotion "whisper", speed 0.92.
- Yelling / exclamations -> emotion "shout", speed 1.05.
- Keep "text" as a verbatim slice of the input.
''';

  static Future<List<Segment>> _direct(
    Fllama f,
    double ctxId,
    String paragraph,
  ) async {
    final formatted = await f.getFormattedChat(
      ctxId,
      messages: [
        RoleContent(role: 'system', content: _systemPrompt),
        RoleContent(role: 'user', content: paragraph),
      ],
    );
    final prompt = formatted ??
        '<|im_start|>system\n$_systemPrompt<|im_end|>\n'
            '<|im_start|>user\n$paragraph<|im_end|>\n'
            '<|im_start|>assistant\n';

    final result = await f.completion(
      ctxId,
      prompt: prompt,
      temperature: 0.2,
      nPredict: 384,
      topP: 0.9,
      stop: ['<|im_end|>', '<|endoftext|>'],
    );

    final text = result?['text']?.toString() ?? result?['content']?.toString() ?? '';
    return _parseDirectorOutput(text, fallback: paragraph);
  }

  // ---- helpers ----

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

  static List<Segment> _parseDirectorOutput(String raw, {required String fallback}) {
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
