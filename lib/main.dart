// One-Stop Content Studio — single-file Flutter app.
//
// Pipeline: Claude 3.5 Sonnet → ElevenLabs TTS → OpenAI DALL-E 3 → FFmpeg.
// Output:   vertical 9:16 MP4 looped in an on-device preview pane.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:ffmpeg_kit_flutter_new/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new/return_code.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:video_player/video_player.dart';

void main() => runApp(const ContentStudioApp());

// ─────────────────────────────────────────────────────────────────────────────
// SECTION: App shell + theme
// ─────────────────────────────────────────────────────────────────────────────

class ContentStudioApp extends StatelessWidget {
  const ContentStudioApp({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = ColorScheme.fromSeed(
      seedColor: Colors.deepPurple,
      brightness: Brightness.dark,
    );
    return MaterialApp(
      title: 'Content Studio',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: scheme,
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFF0E0B1A),
        cardTheme: CardTheme(
          color: const Color(0xFF181428),
          elevation: 0,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          margin: EdgeInsets.zero,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFF1F1A33),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(14),
            borderSide: BorderSide.none,
          ),
        ),
      ),
      home: const ContentStudioHome(),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION: Pipeline state model
// ─────────────────────────────────────────────────────────────────────────────

enum PipelineStep { idle, scripting, voicing, imaging, rendering, done, error }

extension PipelineStepUi on PipelineStep {
  String get label {
    switch (this) {
      case PipelineStep.idle:
        return 'Ready';
      case PipelineStep.scripting:
        return '1/4: Claude is writing the script…';
      case PipelineStep.voicing:
        return '2/4: ElevenLabs is recording voiceovers…';
      case PipelineStep.imaging:
        return '3/4: DALL-E is painting the backgrounds…';
      case PipelineStep.rendering:
        return '4/4: FFmpeg is stitching the final cut…';
      case PipelineStep.done:
        return 'Done — preview is ready.';
      case PipelineStep.error:
        return 'Something broke — see the alert.';
    }
  }

  double get progress {
    switch (this) {
      case PipelineStep.idle:
        return 0;
      case PipelineStep.scripting:
        return 0.15;
      case PipelineStep.voicing:
        return 0.4;
      case PipelineStep.imaging:
        return 0.65;
      case PipelineStep.rendering:
        return 0.9;
      case PipelineStep.done:
        return 1;
      case PipelineStep.error:
        return 0;
    }
  }
}

class Scene {
  Scene({required this.narration, required this.visual});
  final String narration;
  final String visual;
  File? audio;
  File? image;
  File? clip;
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION: Secure key vault
// ─────────────────────────────────────────────────────────────────────────────

class KeyVault {
  KeyVault._();
  static final KeyVault instance = KeyVault._();

  static const _anthropicKey = 'anthropic_key';
  static const _elevenLabsKey = 'elevenlabs_key';
  static const _openAiKey = 'openai_key';

  final _storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  Future<String?> readAnthropic() => _storage.read(key: _anthropicKey);
  Future<String?> readElevenLabs() => _storage.read(key: _elevenLabsKey);
  Future<String?> readOpenAi() => _storage.read(key: _openAiKey);

  Future<void> writeAnthropic(String v) =>
      _storage.write(key: _anthropicKey, value: v.trim());
  Future<void> writeElevenLabs(String v) =>
      _storage.write(key: _elevenLabsKey, value: v.trim());
  Future<void> writeOpenAi(String v) =>
      _storage.write(key: _openAiKey, value: v.trim());
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION: Home screen
// ─────────────────────────────────────────────────────────────────────────────

class ContentStudioHome extends StatefulWidget {
  const ContentStudioHome({super.key});

  @override
  State<ContentStudioHome> createState() => _ContentStudioHomeState();
}

class _ContentStudioHomeState extends State<ContentStudioHome> {
  final _promptController = TextEditingController();
  final _scrollController = ScrollController();

  PipelineStep _step = PipelineStep.idle;
  String _subStatus = '';
  String? _errorMessage;
  File? _finalVideo;
  VideoPlayerController? _playerController;

  @override
  void dispose() {
    _promptController.dispose();
    _scrollController.dispose();
    _playerController?.dispose();
    super.dispose();
  }

  bool get _busy =>
      _step != PipelineStep.idle &&
      _step != PipelineStep.done &&
      _step != PipelineStep.error;

  // ─── Pipeline orchestration ────────────────────────────────────────────────

  Future<void> _runPipeline() async {
    final prompt = _promptController.text.trim();
    if (prompt.isEmpty) {
      _snack('Type a creative prompt first.');
      return;
    }

    final anthropic = await KeyVault.instance.readAnthropic();
    final elevenLabs = await KeyVault.instance.readElevenLabs();
    final openAi = await KeyVault.instance.readOpenAi();
    if (anthropic == null || elevenLabs == null || openAi == null ||
        anthropic.isEmpty || elevenLabs.isEmpty || openAi.isEmpty) {
      _snack('Add all three API keys in Settings first.');
      return;
    }

    setState(() {
      _errorMessage = null;
      _finalVideo = null;
      _playerController?.dispose();
      _playerController = null;
    });

    try {
      setState(() { _step = PipelineStep.scripting; _subStatus = 'Asking Claude for a scene list…'; });
      final scenes = await _callClaude(prompt, anthropic);

      setState(() { _step = PipelineStep.voicing; _subStatus = 'Recording ${scenes.length} voiceovers…'; });
      await _callElevenLabs(scenes, elevenLabs);

      setState(() { _step = PipelineStep.imaging; _subStatus = 'Generating ${scenes.length} images…'; });
      await _callDallE(scenes, openAi);

      setState(() { _step = PipelineStep.rendering; _subStatus = 'Stitching clips with FFmpeg…'; });
      final out = await _renderWithFfmpeg(scenes);

      setState(() { _step = PipelineStep.done; _subStatus = 'Final cut saved.'; _finalVideo = out; });
      await _initPlayer(out);
    } catch (e) {
      setState(() {
        _step = PipelineStep.error;
        _errorMessage = e.toString();
        _subStatus = '';
      });
      _snack(e.toString(), isError: true);
    }
  }

  // ─── Step 1: Claude ────────────────────────────────────────────────────────

  Future<List<Scene>> _callClaude(String prompt, String apiKey) async {
    const system = 'You are a short-form video scriptwriter. Reply with ONLY a '
        'JSON array (no prose, no markdown fences). Each element must be an '
        'object with two string fields: "narration" (under 12 words, spoken '
        'aloud) and "visual" (a vivid 1:1 background description). Produce '
        '4–7 scenes total. Output must be valid JSON parseable as-is.';

    final res = await http
        .post(
          Uri.parse('https://api.anthropic.com/v1/messages'),
          headers: {
            'content-type': 'application/json',
            'x-api-key': apiKey,
            'anthropic-version': '2023-06-01',
          },
          body: jsonEncode({
            'model': 'claude-3-5-sonnet-latest',
            'max_tokens': 1024,
            'system': system,
            'messages': [
              {'role': 'user', 'content': prompt},
            ],
          }),
        )
        .timeout(const Duration(seconds: 60));

    if (res.statusCode != 200) {
      throw 'Claude request failed: ${res.statusCode} ${res.body}';
    }

    final decoded = jsonDecode(res.body) as Map<String, dynamic>;
    final content = decoded['content'] as List<dynamic>?;
    if (content == null || content.isEmpty) {
      throw 'Claude returned an empty response.';
    }
    final text = (content.first as Map<String, dynamic>)['text'] as String? ?? '';
    final jsonText = _extractJsonArray(text);
    final parsed = jsonDecode(jsonText);
    if (parsed is! List) throw 'Claude did not return a JSON array.';

    final scenes = <Scene>[];
    for (final item in parsed) {
      if (item is! Map) throw 'Scene is not a JSON object.';
      final n = item['narration'];
      final v = item['visual'];
      if (n is! String || v is! String || n.isEmpty || v.isEmpty) {
        throw 'Scene missing narration/visual.';
      }
      scenes.add(Scene(narration: n, visual: v));
    }
    if (scenes.isEmpty) throw 'Claude returned zero scenes.';
    return scenes;
  }

  String _extractJsonArray(String raw) {
    final start = raw.indexOf('[');
    final end = raw.lastIndexOf(']');
    if (start == -1 || end == -1 || end < start) {
      throw 'Could not locate a JSON array in Claude output.';
    }
    return raw.substring(start, end + 1);
  }

  // ─── Step 2: ElevenLabs ────────────────────────────────────────────────────

  Future<void> _callElevenLabs(List<Scene> scenes, String apiKey) async {
    const voiceId = '21m00Tcm4TlvDq8ikWAM'; // Rachel — neutral, broadcast-ready.
    final cacheDir = await getTemporaryDirectory();

    for (var i = 0; i < scenes.length; i++) {
      setState(() => _subStatus = 'Voiceover ${i + 1} of ${scenes.length}…');
      final res = await http
          .post(
            Uri.parse('https://api.elevenlabs.io/v1/text-to-speech/$voiceId'),
            headers: {
              'accept': 'audio/mpeg',
              'content-type': 'application/json',
              'xi-api-key': apiKey,
            },
            body: jsonEncode({
              'text': scenes[i].narration,
              'model_id': 'eleven_multilingual_v2',
              'voice_settings': {'stability': 0.5, 'similarity_boost': 0.75},
            }),
          )
          .timeout(const Duration(seconds: 60));
      if (res.statusCode != 200) {
        throw 'ElevenLabs failed on scene ${i + 1}: ${res.statusCode}';
      }
      final f = File('${cacheDir.path}/scene_$i.mp3');
      await f.writeAsBytes(res.bodyBytes, flush: true);
      scenes[i].audio = f;
    }
  }

  // ─── Step 3: DALL-E 3 ──────────────────────────────────────────────────────

  Future<void> _callDallE(List<Scene> scenes, String apiKey) async {
    final cacheDir = await getTemporaryDirectory();

    for (var i = 0; i < scenes.length; i++) {
      setState(() => _subStatus = 'Image ${i + 1} of ${scenes.length}…');
      final res = await http
          .post(
            Uri.parse('https://api.openai.com/v1/images/generations'),
            headers: {
              'content-type': 'application/json',
              'authorization': 'Bearer $apiKey',
            },
            body: jsonEncode({
              'model': 'dall-e-3',
              'prompt': scenes[i].visual,
              'size': '1024x1024',
              'n': 1,
            }),
          )
          .timeout(const Duration(seconds: 90));
      if (res.statusCode != 200) {
        throw 'DALL-E failed on scene ${i + 1}: ${res.statusCode}';
      }
      final decoded = jsonDecode(res.body) as Map<String, dynamic>;
      final data = decoded['data'] as List<dynamic>?;
      final url = (data?.first as Map<String, dynamic>?)?['url'] as String?;
      if (url == null) throw 'DALL-E response missing image URL.';

      final img = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 60));
      if (img.statusCode != 200) {
        throw 'Failed to download image ${i + 1}: ${img.statusCode}';
      }
      final f = File('${cacheDir.path}/scene_$i.png');
      await f.writeAsBytes(img.bodyBytes, flush: true);
      scenes[i].image = f;
    }
  }

  // ─── Step 4: FFmpeg ────────────────────────────────────────────────────────

  Future<File> _renderWithFfmpeg(List<Scene> scenes) async {
    final cacheDir = await getTemporaryDirectory();

    for (var i = 0; i < scenes.length; i++) {
      setState(() => _subStatus = 'Rendering clip ${i + 1} of ${scenes.length}…');
      final img = scenes[i].image!.path;
      final aud = scenes[i].audio!.path;
      final out = '${cacheDir.path}/scene_$i.mp4';

      final cmd =
          '-y -loop 1 -i "$img" -i "$aud" '
          '-vf "scale=1080:1920:force_original_aspect_ratio=increase,'
          'crop=1080:1920,format=yuv420p" '
          '-c:v libx264 -tune stillimage -preset veryfast '
          '-c:a aac -b:a 192k -shortest "$out"';

      final session = await FFmpegKit.execute(cmd);
      final rc = await session.getReturnCode();
      if (!ReturnCode.isSuccess(rc)) {
        final logs = await session.getAllLogsAsString();
        throw 'FFmpeg failed on scene ${i + 1}.\n$logs';
      }
      scenes[i].clip = File(out);
    }

    setState(() => _subStatus = 'Concatenating ${scenes.length} clips…');
    final listFile = File('${cacheDir.path}/concat.txt');
    final lines = scenes
        .map((s) => "file '${s.clip!.path.replaceAll("'", "\\'")}'")
        .join('\n');
    await listFile.writeAsString(lines, flush: true);

    final finalPath = '${cacheDir.path}/final.mp4';
    final concatCmd =
        '-y -f concat -safe 0 -i "${listFile.path}" -c copy "$finalPath"';
    final session = await FFmpegKit.execute(concatCmd);
    final rc = await session.getReturnCode();
    if (!ReturnCode.isSuccess(rc)) {
      final logs = await session.getAllLogsAsString();
      throw 'FFmpeg concat failed.\n$logs';
    }
    return File(finalPath);
  }

  Future<void> _initPlayer(File file) async {
    final ctl = VideoPlayerController.file(file);
    await ctl.initialize();
    await ctl.setLooping(true);
    await ctl.play();
    setState(() => _playerController = ctl);
  }

  void _snack(String msg, {bool isError = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        backgroundColor: isError ? Colors.red.shade700 : null,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  // ─── Layout ────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Content Studio'),
        backgroundColor: Colors.transparent,
        flexibleSpace: const _StudioHeaderGradient(),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          controller: _scrollController,
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const PhilosophyCard(),
              const SizedBox(height: 20),
              const ApiSettingsPanel(),
              const SizedBox(height: 20),
              _PromptInputSection(
                controller: _promptController,
                onGenerate: _busy ? null : _runPipeline,
                busy: _busy,
              ),
              const SizedBox(height: 20),
              _ProgressTracker(step: _step, subStatus: _subStatus, error: _errorMessage),
              const SizedBox(height: 20),
              _VideoPreview(controller: _playerController, file: _finalVideo),
            ],
          ),
        ),
      ),
    );
  }
}

class _StudioHeaderGradient extends StatelessWidget {
  const _StudioHeaderGradient();

  @override
  Widget build(BuildContext context) {
    return const DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF2E1065), Color(0xFF0E0B1A)],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION: Philosophy card
// ─────────────────────────────────────────────────────────────────────────────

class PhilosophyCard extends StatelessWidget {
  const PhilosophyCard({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 18),
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
                colors: [Color(0xFF7C3AED), Color(0xFFEC4899)],
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Why build a custom studio at all?',
                  style: theme.textTheme.titleLarge?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'Copy-pasting between Claude, ElevenLabs, and CapCut works — '
                  'until you do it twice. Then it stops working.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: Colors.white.withOpacity(0.9),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: LayoutBuilder(
              builder: (context, constraints) {
                final wide = constraints.maxWidth > 560;
                const pillars = [
                  _Pillar(
                    icon: Icons.bolt_rounded,
                    title: 'Zero Friction',
                    body: 'One prompt in, one finished short out. Save ≈ 2 hours per video.',
                  ),
                  _Pillar(
                    icon: Icons.style_rounded,
                    title: 'Brand Consistency',
                    body: 'Hardcoded styles and voice. The AI can\'t drift between takes.',
                  ),
                  _Pillar(
                    icon: Icons.all_inclusive_rounded,
                    title: 'Infinite Scale',
                    body: 'Video creation becomes a repeatable utility — not a craft.',
                  ),
                ];
                if (wide) {
                  return Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      for (var i = 0; i < pillars.length; i++) ...[
                        Expanded(child: pillars[i]),
                        if (i != pillars.length - 1) const SizedBox(width: 12),
                      ],
                    ],
                  );
                }
                return Column(
                  children: [
                    for (var i = 0; i < pillars.length; i++) ...[
                      pillars[i],
                      if (i != pillars.length - 1) const SizedBox(height: 12),
                    ],
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _Pillar extends StatelessWidget {
  const _Pillar({required this.icon, required this.title, required this.body});
  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF221C3A),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withOpacity(0.05)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: theme.colorScheme.primary),
          const SizedBox(height: 10),
          Text(title, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(body, style: theme.textTheme.bodySmall?.copyWith(color: Colors.white70)),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION: API settings panel
// ─────────────────────────────────────────────────────────────────────────────

class ApiSettingsPanel extends StatefulWidget {
  const ApiSettingsPanel({super.key});

  @override
  State<ApiSettingsPanel> createState() => _ApiSettingsPanelState();
}

class _ApiSettingsPanelState extends State<ApiSettingsPanel> {
  final _anthropic = TextEditingController();
  final _elevenLabs = TextEditingController();
  final _openAi = TextEditingController();
  bool _revealAnthropic = false;
  bool _revealEleven = false;
  bool _revealOpenAi = false;
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    _hydrate();
  }

  Future<void> _hydrate() async {
    final v = KeyVault.instance;
    _anthropic.text = (await v.readAnthropic()) ?? '';
    _elevenLabs.text = (await v.readElevenLabs()) ?? '';
    _openAi.text = (await v.readOpenAi()) ?? '';
    if (mounted) setState(() => _loaded = true);
  }

  Future<void> _save() async {
    final v = KeyVault.instance;
    await Future.wait([
      v.writeAnthropic(_anthropic.text),
      v.writeElevenLabs(_elevenLabs.text),
      v.writeOpenAi(_openAi.text),
    ]);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('API keys saved to secure storage.'),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  void dispose() {
    _anthropic.dispose();
    _elevenLabs.dispose();
    _openAi.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          tilePadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 4),
          childrenPadding: const EdgeInsets.fromLTRB(18, 0, 18, 18),
          leading: const Icon(Icons.vpn_key_rounded),
          title: const Text('API keys',
              style: TextStyle(fontWeight: FontWeight.w600)),
          subtitle: const Text('Stored in Android Keystore / iOS Keychain'),
          children: [
            if (!_loaded) const LinearProgressIndicator(),
            if (_loaded) ...[
              _keyField('Anthropic (Claude)', _anthropic, _revealAnthropic,
                  () => setState(() => _revealAnthropic = !_revealAnthropic)),
              const SizedBox(height: 12),
              _keyField('ElevenLabs', _elevenLabs, _revealEleven,
                  () => setState(() => _revealEleven = !_revealEleven)),
              const SizedBox(height: 12),
              _keyField('OpenAI (DALL-E 3)', _openAi, _revealOpenAi,
                  () => setState(() => _revealOpenAi = !_revealOpenAi)),
              const SizedBox(height: 16),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton.icon(
                  onPressed: _save,
                  icon: const Icon(Icons.save_rounded),
                  label: const Text('Save keys'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _keyField(String label, TextEditingController ctl, bool reveal,
      VoidCallback toggle) {
    return TextField(
      controller: ctl,
      obscureText: !reveal,
      autocorrect: false,
      enableSuggestions: false,
      inputFormatters: [FilteringTextInputFormatter.deny(RegExp(r'\s'))],
      decoration: InputDecoration(
        labelText: label,
        suffixIcon: IconButton(
          icon: Icon(reveal ? Icons.visibility_off : Icons.visibility),
          onPressed: toggle,
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION: Prompt input
// ─────────────────────────────────────────────────────────────────────────────

class _PromptInputSection extends StatelessWidget {
  const _PromptInputSection({
    required this.controller,
    required this.onGenerate,
    required this.busy,
  });

  final TextEditingController controller;
  final VoidCallback? onGenerate;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(Icons.auto_awesome_rounded),
                const SizedBox(width: 8),
                Text('Creative brief',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w700,
                        )),
              ],
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              maxLines: 4,
              minLines: 3,
              decoration: const InputDecoration(
                hintText:
                    'e.g. "A 30-second short on why ancient Stoics would have loved morning runs."',
              ),
            ),
            const SizedBox(height: 14),
            SizedBox(
              height: 48,
              child: FilledButton.icon(
                onPressed: onGenerate,
                icon: busy
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.movie_creation_rounded),
                label: Text(busy ? 'Working…' : 'Generate Video'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION: Progress tracker
// ─────────────────────────────────────────────────────────────────────────────

class _ProgressTracker extends StatelessWidget {
  const _ProgressTracker({
    required this.step,
    required this.subStatus,
    required this.error,
  });

  final PipelineStep step;
  final String subStatus;
  final String? error;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isError = step == PipelineStep.error;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  isError ? Icons.error_outline : Icons.timeline_rounded,
                  color: isError ? Colors.redAccent : theme.colorScheme.primary,
                ),
                const SizedBox(width: 8),
                Text('Pipeline',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w700,
                    )),
              ],
            ),
            const SizedBox(height: 12),
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: LinearProgressIndicator(
                value: step.progress == 0 ? null : step.progress,
                minHeight: 8,
                backgroundColor: Colors.white12,
                color: isError ? Colors.redAccent : null,
              ),
            ),
            const SizedBox(height: 12),
            Text(step.label,
                style: theme.textTheme.bodyLarge
                    ?.copyWith(fontWeight: FontWeight.w600)),
            if (subStatus.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(subStatus,
                  style: theme.textTheme.bodySmall?.copyWith(color: Colors.white70)),
            ],
            if (isError && error != null) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.red.withOpacity(0.08),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.red.withOpacity(0.3)),
                ),
                child: Text(error!,
                    style: const TextStyle(color: Colors.redAccent, fontSize: 12)),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION: Video preview
// ─────────────────────────────────────────────────────────────────────────────

class _VideoPreview extends StatefulWidget {
  const _VideoPreview({required this.controller, required this.file});
  final VideoPlayerController? controller;
  final File? file;

  @override
  State<_VideoPreview> createState() => _VideoPreviewState();
}

class _VideoPreviewState extends State<_VideoPreview> {
  bool _muted = false;

  @override
  Widget build(BuildContext context) {
    final ctl = widget.controller;
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(Icons.play_circle_rounded),
                const SizedBox(width: 8),
                Text('Preview',
                    style: theme.textTheme.titleMedium
                        ?.copyWith(fontWeight: FontWeight.w700)),
                const Spacer(),
                if (ctl != null && ctl.value.isInitialized) ...[
                  IconButton(
                    tooltip: _muted ? 'Unmute' : 'Mute',
                    onPressed: () async {
                      await ctl.setVolume(_muted ? 1 : 0);
                      setState(() => _muted = !_muted);
                    },
                    icon: Icon(_muted ? Icons.volume_off : Icons.volume_up),
                  ),
                  IconButton(
                    tooltip: 'Replay',
                    onPressed: () async {
                      await ctl.seekTo(Duration.zero);
                      await ctl.play();
                    },
                    icon: const Icon(Icons.replay_rounded),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 12),
            AspectRatio(
              aspectRatio: 9 / 16,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(14),
                child: ctl != null && ctl.value.isInitialized
                    ? VideoPlayer(ctl)
                    : Container(
                        color: const Color(0xFF1F1A33),
                        alignment: Alignment.center,
                        child: Text(
                          widget.file == null
                              ? 'Your video will appear here.'
                              : 'Initializing player…',
                          style: theme.textTheme.bodyMedium
                              ?.copyWith(color: Colors.white60),
                        ),
                      ),
              ),
            ),
            if (widget.file != null) ...[
              const SizedBox(height: 10),
              Text(widget.file!.path,
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: Colors.white54),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis),
            ],
          ],
        ),
      ),
    );
  }
}
