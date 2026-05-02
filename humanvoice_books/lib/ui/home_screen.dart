import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:permission_handler/permission_handler.dart';

import '../bloc/book_bloc.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _titleCtrl = TextEditingController(text: 'My Audiobook');
  final _authorCtrl = TextEditingController(text: 'Unknown');

  @override
  void initState() {
    super.initState();
    // Request permissions early; defer model download to user-initiated tap so
    // we never blow ~600MB on someone who only wants to browse the library.
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _requestPermissions();
      if (mounted) context.read<BookBloc>().add(const RefreshLibrary());
    });
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _authorCtrl.dispose();
    super.dispose();
  }

  Future<void> _requestPermissions() async {
    await [
      Permission.notification,
      Permission.audio,
      Permission.microphone,
    ].request();
  }

  Future<void> _pickEpub() async {
    final r = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['epub'],
    );
    final path = r?.files.single.path;
    if (path != null && mounted) {
      context.read<BookBloc>().add(ImportEpub(path));
    }
  }

  bool _canProduce(Stage s) =>
      s == Stage.modelsReady || s == Stage.done || s == Stage.error;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('HumanVoice Books'),
        actions: [
          IconButton(
            tooltip: 'Refresh library',
            icon: const Icon(Icons.refresh),
            onPressed: () =>
                context.read<BookBloc>().add(const RefreshLibrary()),
          ),
        ],
      ),
      body: BlocBuilder<BookBloc, BookState>(
        builder: (ctx, state) {
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _StageBanner(state: state),
              const SizedBox(height: 12),
              _ProgressRow(state: state),
              const SizedBox(height: 16),
              const _SectionLabel('1. Models'),
              FilledButton.tonalIcon(
                onPressed: state.stage == Stage.downloadingModels
                    ? null
                    : () => ctx.read<BookBloc>().add(const EnsureModels()),
                icon: const Icon(Icons.cloud_download),
                label: Text(
                  state.stage == Stage.modelsReady ||
                          state.stage == Stage.done
                      ? 'Models ready (re-verify)'
                      : 'Initialize models (Kokoro-82M, ~330 MB)',
                ),
              ),
              const SizedBox(height: 24),
              const _SectionLabel('2. Source EPUB'),
              FilledButton.icon(
                onPressed: _pickEpub,
                icon: const Icon(Icons.menu_book),
                label: Text(state.loadedEpubName ?? 'Pick EPUB'),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _titleCtrl,
                decoration: const InputDecoration(
                  labelText: 'Book title',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _authorCtrl,
                decoration: const InputDecoration(
                  labelText: 'Author',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: _canProduce(state.stage) &&
                        state.loadedEpubName != null
                    ? () => ctx.read<BookBloc>().add(
                          StartProduction(
                            title: _titleCtrl.text,
                            author: _authorCtrl.text,
                          ),
                        )
                    : null,
                icon: const Icon(Icons.record_voice_over),
                label: const Text('Produce audiobook'),
              ),
              if (state.chapters.isNotEmpty) ...[
                const SizedBox(height: 24),
                const _SectionLabel('Director output'),
                ...state.chapters.take(8).map(
                      (c) => ListTile(
                        dense: true,
                        leading: CircleAvatar(child: Text('${c.index + 1}')),
                        title: Text(c.title),
                        subtitle: Text('${c.segments.length} acted lines'),
                      ),
                    ),
                if (state.chapters.length > 8)
                  Padding(
                    padding: const EdgeInsets.only(left: 16),
                    child: Text(
                      '… and ${state.chapters.length - 8} more',
                      style: Theme.of(ctx).textTheme.bodySmall,
                    ),
                  ),
              ],
              const SizedBox(height: 24),
              const _SectionLabel('3. Library'),
              if (state.library.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Text(
                    'No audiobooks yet. Generate one above.',
                    style: Theme.of(ctx).textTheme.bodyMedium,
                  ),
                )
              else
                ...state.library.map((f) => _LibraryTile(file: f)),
            ],
          );
        },
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String text;
  const _SectionLabel(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Text(
        text,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
              color: Theme.of(context).colorScheme.primary,
              letterSpacing: 0.5,
            ),
      ),
    );
  }
}

class _ProgressRow extends StatelessWidget {
  final BookState state;
  const _ProgressRow({required this.state});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LinearProgressIndicator(
          value: state.progress == 0 ? null : state.progress,
        ),
        const SizedBox(height: 6),
        Text(
          state.message.isEmpty ? ' ' : state.message,
          style: Theme.of(context).textTheme.bodySmall,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}

class _StageBanner extends StatelessWidget {
  final BookState state;
  const _StageBanner({required this.state});

  @override
  Widget build(BuildContext context) {
    final color = switch (state.stage) {
      Stage.error => Colors.red,
      Stage.done => Colors.green,
      Stage.idle => Colors.grey,
      _ => Theme.of(context).colorScheme.primary,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color),
      ),
      child: Row(
        children: [
          Icon(_iconFor(state.stage), color: color),
          const SizedBox(width: 8),
          Text(
            state.stage.name.toUpperCase(),
            style: TextStyle(color: color, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }

  IconData _iconFor(Stage s) {
    switch (s) {
      case Stage.idle:
        return Icons.hourglass_empty;
      case Stage.downloadingModels:
        return Icons.cloud_download;
      case Stage.modelsReady:
        return Icons.check_circle_outline;
      case Stage.analyzing:
        return Icons.auto_stories;
      case Stage.rendering:
        return Icons.graphic_eq;
      case Stage.muxing:
        return Icons.movie_creation;
      case Stage.done:
        return Icons.task_alt;
      case Stage.error:
        return Icons.error_outline;
    }
  }
}

class _LibraryTile extends StatelessWidget {
  final File file;
  const _LibraryTile({required this.file});

  String _bytesHuman(int b) {
    if (b < 1024) return '${b}B';
    if (b < 1024 * 1024) return '${(b / 1024).toStringAsFixed(0)} KB';
    if (b < 1024 * 1024 * 1024) return '${(b / 1048576).toStringAsFixed(1)} MB';
    return '${(b / 1073741824).toStringAsFixed(2)} GB';
  }

  @override
  Widget build(BuildContext context) {
    final stat = file.statSync();
    final name = file.path.split('/').last;
    return ListTile(
      leading: const Icon(Icons.headphones),
      title: Text(name),
      subtitle: Text(
        '${_bytesHuman(stat.size)} • ${stat.modified.toLocal()}',
      ),
      trailing: const Icon(Icons.play_arrow),
      onTap: () {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('TODO: play ${file.path}')),
        );
      },
    );
  }
}
