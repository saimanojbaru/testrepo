import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:permission_handler/permission_handler.dart';

import '../bloc/book_bloc.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _titleCtrl = TextEditingController(text: 'My Audiobook');
  final _authorCtrl = TextEditingController(text: 'Unknown');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _requestPermissions();
      if (mounted) context.read<BookBloc>().add(const EnsureModels());
    });
  }

  Future<void> _requestPermissions() async {
    await [
      Permission.notification,
      Permission.audio,
      Permission.microphone,
    ].request();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('HumanVoice Books')),
      body: BlocBuilder<BookBloc, BookState>(
        builder: (ctx, state) {
          return Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _StageBanner(state: state),
                const SizedBox(height: 16),
                LinearProgressIndicator(
                  value: state.progress == 0 ? null : state.progress,
                ),
                const SizedBox(height: 8),
                Text(state.message, style: Theme.of(ctx).textTheme.bodySmall),
                const SizedBox(height: 24),
                TextField(
                  controller: _titleCtrl,
                  decoration: const InputDecoration(labelText: 'Book title'),
                ),
                TextField(
                  controller: _authorCtrl,
                  decoration: const InputDecoration(labelText: 'Author'),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: () async {
                          final r = await FilePicker.platform.pickFiles(
                            type: FileType.custom,
                            allowedExtensions: ['epub'],
                          );
                          final path = r?.files.single.path;
                          if (path != null && context.mounted) {
                            context.read<BookBloc>().add(ImportEpub(path));
                          }
                        },
                        icon: const Icon(Icons.menu_book),
                        label: const Text('Pick EPUB'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton.tonalIcon(
                        onPressed: state.stage == Stage.modelsReady ||
                                state.stage == Stage.done ||
                                state.stage == Stage.error
                            ? () => context.read<BookBloc>().add(StartProduction(
                                  title: _titleCtrl.text,
                                  author: _authorCtrl.text,
                                ))
                            : null,
                        icon: const Icon(Icons.record_voice_over),
                        label: const Text('Produce'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                if (state.chapters.isNotEmpty)
                  Expanded(
                    child: ListView.builder(
                      itemCount: state.chapters.length,
                      itemBuilder: (_, i) {
                        final c = state.chapters[i];
                        return ListTile(
                          dense: true,
                          leading: CircleAvatar(child: Text('${i + 1}')),
                          title: Text(c.title),
                          subtitle: Text('${c.segments.length} acted lines'),
                        );
                      },
                    ),
                  ),
              ],
            ),
          );
        },
      ),
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
